import Foundation

public enum CameraError: Error, LocalizedError {
    case invalidAddress, invalidPath, invalidResponse, http(Int), rejected(String), incomplete
    public var errorDescription: String? {
        switch self {
        case .invalidAddress: return "Invalid local IPv4 camera address."
        case .invalidPath: return "Unsafe camera file path."
        case .invalidResponse: return "The camera returned an invalid response."
        case .http(let code): return "Camera HTTP \(code)."
        case .rejected(let value): return "Camera rejected the command: \(value.prefix(160))"
        case .incomplete: return "The download size does not match the camera file."
        }
    }
}
public struct CameraFile: Codable, Identifiable, Hashable, Sendable {
    public let path: String
    public let create: String
    public let time: String
    public let size: String
    public var id: String { path }
    public var name: String { (path as NSString).lastPathComponent }
    public var video: Bool { path.lowercased().hasSuffix(".mp4") }
    public var bytes: Int64 { Int64(size) ?? 0 }
    public var thumbnail: String { (path as NSString).deletingPathExtension + ".THM" }
}
public enum CameraProtocol {
    public static let modes = ["NormalPhoto", "NormalVideo", "TimerPhoto", "Burst", "PhotoLapse", "VideoLapse", "CarMode", "SlowRec"]
    public static func base(_ input: String) throws -> URL {
        let raw = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let c = URLComponents(string: raw.contains("://") ? raw : "http://" + raw), c.scheme == "http", c.user == nil, c.password == nil, c.query == nil, c.fragment == nil, c.path.isEmpty || c.path == "/", let host = c.host else { throw CameraError.invalidAddress }
        let parts = host.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4, parts.allSatisfy({ !$0.isEmpty && $0.allSatisfy(\.isNumber) && $0.count <= 3 && ($0.count == 1 || !$0.hasPrefix("0")) }), let a = Int(parts[0]), let b = Int(parts[1]), parts.allSatisfy({ (0...255).contains(Int($0) ?? -1) }), (a == 10 || (a == 172 && (16...31).contains(b)) || (a == 192 && b == 168) || (a == 169 && b == 254)), c.port == nil || (1...65535).contains(c.port!) else { throw CameraError.invalidAddress }
        guard let result = c.url else { throw CameraError.invalidAddress }; return result
    }
    public static func safePath(_ path: String, thumbnail: Bool = false) throws -> String {
        let parts = path.split(separator: "/", omittingEmptySubsequences: false)
        guard parts.count >= 4, parts[0] == "sd", parts[1] == "DCIM", parts.allSatisfy({ !$0.isEmpty && $0 != "." && $0 != ".." && $0.utf8.allSatisfy { (48...57).contains($0) || (65...90).contains($0) || (97...122).contains($0) || [45,46,95].contains($0) } }) else { throw CameraError.invalidPath }
        let ext = (path as NSString).pathExtension.lowercased()
        guard (thumbnail ? ["thm", "jpg", "jpeg"] : ["jpg", "jpeg", "mp4", "dng"]).contains(ext) else { throw CameraError.invalidPath }
        return path
    }
    public static func variables(_ raw: String) -> [String: String] {
        let regex = try! NSRegularExpression(pattern: #"var\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"([^"\r\n]*)"\s*;"#)
        var values: [String: String] = [:]
        for m in regex.matches(in: raw, range: NSRange(raw.startIndex..., in: raw)) {
            if let k = Range(m.range(at: 1), in: raw), let v = Range(m.range(at: 2), in: raw) { values[String(raw[k])] = String(raw[v]) }
        }
        return values
    }
    public static func files(_ data: Data) throws -> [CameraFile] {
        let all = try JSONDecoder().decode([CameraFile].self, from: data)
        var seen = Set<String>()
        return try all.filter { item in
            _ = try safePath(item.path)
            guard item.bytes > 0 else { throw CameraError.invalidResponse }
            return seen.insert(item.path).inserted
        }
    }
    public static func command(_ base: URL, _ name: String, _ args: [(String, String)] = []) -> URL {
        var c = URLComponents(url: base.appendingPathComponent("cgi-bin/hi3510/" + name), resolvingAgainstBaseURL: false)!
        if !args.isEmpty { c.queryItems = args.map { URLQueryItem(name: $0.0, value: $0.1) } }
        return c.url!
    }
    public static func captureCommand(mode: String, start: Bool) -> (String, [(String, String)]) {
        start && mode == "VideoLapse" ? ("photo.cgi", [("-type", "photo")]) : ("record.cgi", [("-cmd", start ? "start" : "stop")])
    }
}
