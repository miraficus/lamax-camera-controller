import SwiftUI
import UIKit
import MobileVLCKit

struct VLCPreview: UIViewRepresentable {
    let address: String
    let restart: UUID
    @Binding var status: String
    func makeCoordinator() -> Coordinator { Coordinator(status: $status) }
    func makeUIView(context: Context) -> UIView { let view = UIView(); view.backgroundColor = .black; return view }
    func updateUIView(_ view: UIView, context: Context) {
        guard let host = URLComponents(string: address)?.host, let url = URL(string: "rtsp://\(host):554/livestream/12") else { return }
        let key = url.absoluteString + restart.uuidString
        if context.coordinator.key != key {
            context.coordinator.stop(); context.coordinator.key = key
            let media = VLCMedia(url: url); media.addOption(":rtsp-tcp"); media.addOption(":network-caching=250"); media.addOption(":no-audio")
            context.coordinator.player.drawable = view; context.coordinator.player.media = media; context.coordinator.player.play()
        }
    }
    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) { coordinator.stop() }
    final class Coordinator: NSObject, VLCMediaPlayerDelegate {
        let player = VLCMediaPlayer()
        var key = ""
        var status: Binding<String>
        init(status: Binding<String>) { self.status = status; super.init(); player.delegate = self }
        func stop() { player.stop(); player.drawable = nil }
        func mediaPlayerStateChanged(_ aNotification: Notification) {
            let state = player.state
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                if state == .playing { self.status.wrappedValue = "" }
                else if state == .error { self.status.wrappedValue = tr("Náhled není dostupný. Zkontroluj Wi-Fi a zkus obnovit obraz.", "Preview unavailable. Check Wi-Fi and restart the preview.") }
            }
        }
    }
}
struct PreviewPane: View {
    let address: String
    @State private var restart = UUID()
    @State private var status = tr("Připojuji náhled…", "Connecting preview…")
    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black
            VLCPreview(address: address, restart: restart, status: $status)
            if !status.isEmpty { Text(status).font(.caption).foregroundColor(.white).multilineTextAlignment(.center).padding(40).frame(maxWidth: .infinity, maxHeight: .infinity) }
            Button { status = tr("Připojuji náhled…", "Connecting preview…"); restart = UUID() } label: { Image(systemName: "arrow.clockwise").frame(width: 44,height: 44).background(Theme.card.opacity(0.9)).clipShape(Circle()) }.padding(8).accessibilityLabel(tr("Obnovit náhled", "Restart preview"))
        }.clipShape(RoundedRectangle(cornerRadius: 20))
    }
}
