import SwiftUI
import CameraCore
import UIKit

struct ShareSheet: UIViewControllerRepresentable {
    let urls: [URL]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: urls,applicationActivities: nil) }
    func updateUIViewController(_ uiViewController: UIActivityViewController,context: Context) { }
}
struct Thumbnail: View {
    let file: CameraFile
    let address: String
    let enabled: Bool
    @State private var image: UIImage?
    var body: some View {
        ZStack {
            Theme.card
            if let image = image { Image(uiImage: image).resizable().scaledToFill() }
            else { Image(systemName: file.video ? "video" : "photo").foregroundColor(.secondary) }
        }.frame(height: 100).clipped().cornerRadius(16)
        .task(id: address + file.path + String(enabled)) {
            guard enabled else { return }
            do { let c = try CameraHTTP(base: address); defer { c.close() }; image = try await c.image(file) } catch { }
        }
    }
}
struct GalleryView: View {
    @EnvironmentObject var model: CameraModel
    @State private var filter = 0
    private var visible: [CameraFile] { model.files.filter { filter == 0 || (filter == 2) == $0.video } }
    var body: some View {
        VStack(spacing: 12) {
            ScrollView {
                VStack(spacing: 14) {
                    ConnectionHeader(); PageHeading(title: tr("Galerie", "Gallery"))
                    Text("\(visible.count) " + tr("souborů", "files")).font(.caption).foregroundColor(.secondary).frame(maxWidth: .infinity,alignment: .leading)
                    Picker(tr("Filtr", "Filter"),selection: $filter) {
                        Text(tr("Vše", "All")).tag(0); Text(tr("Fotky", "Photos")).tag(1); Text(tr("Videa", "Videos")).tag(2)
                    }.pickerStyle(.segmented)
                    Text(tr("Označeno", "Selected") + ": \(model.selected.count)").font(.caption).foregroundColor(.secondary).frame(maxWidth: .infinity,alignment: .leading)
                    HStack(spacing: 8) {
                        Button(tr("Označit vše", "Select all")) { model.selected.formUnion(visible.map(\.path)) }.buttonStyle(CapsuleAction())
                        Button(tr("Zrušit označení", "Clear selection")) { model.selected = [] }.buttonStyle(CapsuleAction()).disabled(model.selected.isEmpty)
                    }.font(.caption)
                    if visible.isEmpty { Text(model.connected ? tr("Žádné soubory v tomto filtru.", "No files in this filter.") : tr("Zapni Wi-Fi kamery, připoj iPhone k její síti v Nastavení a klepni na Připojit. Připojení bez internetu je v pořádku.", "Enable camera Wi-Fi, join its network in iPhone Settings, then tap Connect. A connection without internet is expected.")).foregroundColor(.secondary).padding(.vertical,24) }
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 125),spacing: 12)],spacing: 16) {
                        ForEach(visible) { file in tile(file) }
                    }
                }.padding(.horizontal,16).padding(.top,16)
            }
            if !model.transfer.isEmpty { Text(model.transfer).font(.caption).lineLimit(2).padding(.horizontal,16) }
            if model.downloading { Button(tr("Zrušit stahování", "Cancel download")) { model.cancelDownloads() }.buttonStyle(CapsuleAction()).padding(.horizontal,16) }
            else {
                VStack(spacing: 8) {
                    Button { model.downloadSelected() } label: { Label(tr("Stáhnout označené", "Download selected") + " (\(model.selected.count))",systemImage: "arrow.down.to.line") }.buttonStyle(CapsuleAction(primary: true)).disabled(model.selected.isEmpty)
                    if !model.selected.isDisjoint(with: model.saved) { Button { model.shareSaved() } label: { Label(tr("Sdílet uložené", "Share saved"),systemImage: "square.and.arrow.up") }.font(.caption) }
                }.padding(.horizontal,16)
            }
        }.padding(.bottom,8).background(Theme.bg.ignoresSafeArea())
        .sheet(isPresented: Binding(get: { !model.shareURLs.isEmpty },set: { if !$0 { model.shareURLs = [] } })) { ShareSheet(urls: model.shareURLs) }
    }
    private func tile(_ file: CameraFile) -> some View {
        VStack(alignment: .leading,spacing: 4) {
            ZStack(alignment: .topTrailing) {
                Thumbnail(file: file,address: model.address,enabled: !model.downloading)
                Button {
                    if model.selected.contains(file.path) { model.selected.remove(file.path) } else { model.selected.insert(file.path) }
                } label: { Image(systemName: model.selected.contains(file.path) ? "checkmark.circle.fill" : "circle").font(.title2).foregroundColor(model.selected.contains(file.path) ? Theme.accent : .white).padding(10).background(Color.black.opacity(0.2)).clipShape(Circle()) }.accessibilityLabel((model.selected.contains(file.path) ? tr("Zrušit označení", "Deselect") : tr("Označit", "Select")) + " " + file.name)
                if file.video { Image(systemName: "play.circle.fill").padding(8).frame(maxWidth: .infinity,maxHeight: .infinity,alignment: .bottomLeading).allowsHitTesting(false) }
            }
            Text(file.name).font(.caption).lineLimit(1).truncationMode(.middle)
            HStack { Text(ByteCountFormatter.string(fromByteCount: file.bytes,countStyle: .file)); if model.saved.contains(file.path) { Image(systemName: "checkmark") } }.font(.caption2).foregroundColor(.secondary)
        }
    }
}
