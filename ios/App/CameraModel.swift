import Foundation
import UIKit
import ImageIO
import CameraCore

func tr(_ cs: String, _ en: String) -> String {
    let code = UserDefaults.standard.string(forKey: "language") ?? "system"
    let lang = code == "system" ? (Locale.preferredLanguages.first ?? "en") : code
    return lang.hasPrefix("cs") || lang.hasPrefix("sk") ? cs : en
}
func modeTitle(_ code: String) -> String {
    switch code {
    case "NormalPhoto": return tr("Běžná fotografie", "Single photo")
    case "NormalVideo": return tr("Běžné video", "Normal video")
    case "TimerPhoto": return tr("Samospoušť", "Self-timer")
    case "Burst": return tr("Sériové snímání", "Burst photos")
    case "PhotoLapse": return tr("Časosběrné fotografie", "Photo time-lapse")
    case "VideoLapse": return tr("Časosběrné video", "Time-lapse video")
    case "CarMode": return tr("Záznam ve smyčce", "Loop recording")
    case "SlowRec": return tr("Zpomalené video", "Slow motion")
    default: return code
    }
}
final class CameraHTTP: NSObject, URLSessionTaskDelegate {
    let base: URL
    private lazy var session: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.allowsCellularAccess = false; c.allowsExpensiveNetworkAccess = false
        c.connectionProxyDictionary = [:]; c.waitsForConnectivity = false
        c.timeoutIntervalForRequest = 15; c.timeoutIntervalForResource = 300
        c.httpMaximumConnectionsPerHost = 2
        return URLSession(configuration: c, delegate: self, delegateQueue: nil)
    }()
    init(base: String) throws { self.base = try CameraProtocol.base(base); super.init() }
    func close() { session.invalidateAndCancel() }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
    func get(_ name: String, _ args: [(String,String)] = []) async throws -> String {
        let data = try await bytes(CameraProtocol.command(base, name, args), limit: 2 * 1024 * 1024)
        guard let raw = String(data: data, encoding: .utf8) else { throw CameraError.invalidResponse }; return raw
    }
    func bytes(_ url: URL, limit: Int = 12 * 1024 * 1024) async throws -> Data {
        let (data, response) = try await session.data(from: url)
        guard let r = response as? HTTPURLResponse else { throw CameraError.invalidResponse }
        guard r.statusCode == 200 else { throw CameraError.http(r.statusCode) }
        guard data.count <= limit else { throw CameraError.invalidResponse }; return data
    }
    func mutation(_ name: String, _ args: [(String,String)]) async throws {
        let raw = try await get(name, args)
        guard raw.localizedCaseInsensitiveContains("Success") || raw.contains("SvrFuncResult=\"0\"") else { throw CameraError.rejected(raw) }
    }
    func list() async throws -> [CameraFile] {
        let raw = try await get("getfilecount.cgi")
        guard let count = Int(CameraProtocol.variables(raw)["count"] ?? ""), (0...10000).contains(count) else { throw CameraError.invalidResponse }
        var result: [CameraFile] = []; var seen = Set<String>()
        if count > 0 {
            for start in stride(from: 1, through: count, by: 100) {
                try Task.checkCancellation()
                let page = try await get("getfilelistinfoios.cgi", [("-start",String(start)),("-end",String(min(start+99,count)))])
                for file in try CameraProtocol.files(Data(page.utf8)) where seen.insert(file.path).inserted { result.append(file) }
            }
        }
        return result
    }
    func image(_ file: CameraFile) async throws -> UIImage? {
        let path = try CameraProtocol.safePath(file.thumbnail, thumbnail: true)
        do { if let image = thumbnailImage(try await bytes(base.appendingPathComponent(path))) { return image } }
        catch { if file.video { return nil } }
        guard !file.video else { return nil }
        return thumbnailImage(try await bytes(base.appendingPathComponent(try CameraProtocol.safePath(file.path))))
    }
    private func thumbnailImage(_ data: Data) -> UIImage? {
        // Decode a bounded thumbnail instead of retaining 12 MP bitmaps on older iPhones.
        guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceCreateThumbnailWithTransform: true, kCGImageSourceThumbnailMaxPixelSize: 400, kCGImageSourceShouldCacheImmediately: true] as CFDictionary) else { return nil }
        return UIImage(cgImage: image)
    }
    func download(_ file: CameraFile, to destination: URL) async throws {
        let url = base.appendingPathComponent(try CameraProtocol.safePath(file.path))
        let (temporary, response) = try await session.download(from: url)
        defer { try? FileManager.default.removeItem(at: temporary) }
        guard let r = response as? HTTPURLResponse else { throw CameraError.invalidResponse }
        guard r.statusCode == 200 else { throw CameraError.http(r.statusCode) }
        let size = (try FileManager.default.attributesOfItem(atPath: temporary.path)[.size] as? NSNumber)?.int64Value
        guard size == file.bytes else { throw CameraError.incomplete }
        try Task.checkCancellation()
        try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
        let staged = destination.deletingLastPathComponent().appendingPathComponent(UUID().uuidString + ".part")
        defer { try? FileManager.default.removeItem(at: staged) }
        try FileManager.default.copyItem(at: temporary, to: staged)
        if FileManager.default.fileExists(atPath: destination.path) { _ = try FileManager.default.replaceItemAt(destination, withItemAt: staged) }
        else { try FileManager.default.moveItem(at: staged, to: destination) }
    }
}

@MainActor final class CameraModel: ObservableObject {
    @Published var connected = false
    @Published var name = ""
    @Published var battery = "—"
    @Published var files: [CameraFile] = []
    @Published var mode = "NormalPhoto"
    @Published var modes = CameraProtocol.modes
    @Published var busy = false
    @Published var notice = ""
    @Published var transfer = ""
    @Published var downloading = false
    @Published var selected = Set<String>()
    @Published var saved = Set<String>()
    @Published var shareURLs: [URL] = []
    var address: String { UserDefaults.standard.string(forKey: "cameraAddress") ?? "http://192.168.0.1" }
    private var transferTask: Task<Void,Never>?
    private var photoTask: Task<Void,Never>?
    func perform(_ work: @escaping (CameraHTTP) async throws -> Void) {
        guard !busy else { return }; busy = true
        Task {
            defer { busy = false }
            do { let c = try CameraHTTP(base: address); defer { c.close() }; try await work(c) }
            catch is CancellationError { }
            catch { notice = tr("Nepodařilo se spojit s kamerou nebo provést příkaz. Zkontroluj Wi-Fi a povolení Místní síť v Nastavení.", "Could not reach the camera or complete the command. Check Wi-Fi and Local Network permission in Settings.") + "\n" + error.localizedDescription }
        }
    }
    func connect() {
        perform { c in
            let vars = CameraProtocol.variables(try await c.get("getdeviceattr.cgi"))
            guard !vars.isEmpty else { throw CameraError.invalidResponse }
            self.name = vars["name"] ?? ""; self.connected = true
            self.battery = CameraProtocol.variables(try await c.get("getbatterycapacity.cgi"))["capacity"].map { $0 + " %" } ?? "—"
            let options = CameraProtocol.variables(try await c.get("getallworkmode.cgi"))
            let available = ["photo","video"].flatMap { (options[$0] ?? "").split(separator: ",").map(String.init) }
            if !available.isEmpty { var seen = Set<String>(); self.modes = available.filter { seen.insert($0).inserted } }
            self.mode = CameraProtocol.variables(try await c.get("getcurworkmode.cgi"))["workmode"] ?? self.mode
            self.files = try await c.list(); self.selected.formIntersection(Set(self.files.map(\.path))); self.checkSaved()
        }
    }
    func refresh() { if !connected { connect(); return }; perform { c in self.files = try await c.list(); self.selected.formIntersection(Set(self.files.map(\.path))); self.checkSaved() } }
    func setMode(_ value: String) { guard value != mode else { return }; perform { c in try await c.mutation("setcurworkmode.cgi", [("-workmode",value)]); self.mode = value } }
    func photo() { perform { c in try await c.mutation("photo.cgi", [("-type","photo")]); self.watchFiles() } }
    func recording(_ start: Bool) {
        perform { c in
            let command = CameraProtocol.captureCommand(mode: self.mode, start: start)
            try await c.mutation(command.0, command.1)
            self.notice = tr("Kamera přijala příkaz.", "Camera accepted the command.")
            if !start { self.watchFiles() }
        }
    }
    private func watchFiles() {
        photoTask?.cancel()
        photoTask = Task {
            for seconds in [UInt64(2),3,5,10] {
                do { try await Task.sleep(nanoseconds: seconds*1_000_000_000); try Task.checkCancellation()
                    let c = try CameraHTTP(base: address); defer { c.close() }
                    files = try await c.list(); checkSaved()
                } catch { return }
            }
        }
    }
    func syncTime() { let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyyMMddHHmmss"
        perform { c in try await c.mutation("setsystime.cgi", [("-time",f.string(from: Date()))]); self.notice = tr("Čas byl odeslán kameře.", "Time sent to camera.") }
    }
    func saveAddress(_ raw: String) {
        guard !busy && !downloading else { return }
        do { let base = try CameraProtocol.base(raw); photoTask?.cancel(); UserDefaults.standard.set(base.absoluteString, forKey: "cameraAddress"); connected = false; name = ""; files = []; selected = []; saved = [] }
        catch { notice = tr("Zadej místní IPv4 adresu, například http://192.168.0.1.", "Enter a local IPv4 address, such as http://192.168.0.1.") }
    }
    func localURL(_ file: CameraFile) throws -> URL {
        let host = try CameraProtocol.base(address).host!
        let path = try CameraProtocol.safePath(file.path)
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("Downloads/" + host + "/" + path)
    }
    private func exists(_ file: CameraFile) -> Bool { guard let url = try? localURL(file), let attrs = try? FileManager.default.attributesOfItem(atPath: url.path), let size = attrs[.size] as? NSNumber else { return false }; return size.int64Value == file.bytes }
    func checkSaved() { saved = Set(files.filter { exists($0) }.map(\.path)) }
    func shareSaved() { shareURLs = files.filter { selected.contains($0.path) && exists($0) }.compactMap { try? localURL($0) } }
    func downloadSelected() {
        guard !downloading else { return }; let queue = files.filter { selected.contains($0.path) }; guard !queue.isEmpty else { return }
        downloading = true; transfer = tr("Stahuji…", "Downloading…")
        transferTask = Task {
            var completed = 0, skipped = 0, errors = 0; var cancelled = false
            defer { downloading = false; checkSaved(); transfer = tr("Uloženo", "Saved") + " \(completed) · " + tr("Přeskočeno", "Skipped") + " \(skipped) · " + tr("Chyby", "Errors") + " \(errors)" + (cancelled ? " · " + tr("Zrušeno", "Cancelled") : "") }
            do { let c = try CameraHTTP(base: address); defer { c.close() }
                for (index,file) in queue.enumerated() {
                    try Task.checkCancellation(); transfer = "\(index+1)/\(queue.count) · \(file.name)"
                    if exists(file) { skipped += 1; continue }
                    do { try await c.download(file, to: localURL(file)); completed += 1 }
                    catch { if Task.isCancelled { throw CancellationError() }; errors += 1 }
                }
            } catch { cancelled = Task.isCancelled; if !cancelled { errors += 1 } }
        }
    }
    func cancelDownloads() { transferTask?.cancel() }
}
