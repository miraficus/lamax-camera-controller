import SwiftUI
import CameraCore

@main @MainActor struct X10App: App {
    @StateObject private var model = CameraModel()
    @AppStorage("language") private var language = "system"
    var body: some Scene { WindowGroup { RootView().environmentObject(model).id(language).preferredColorScheme(.dark) } }
}
enum Theme {
    static let bg = Color(red: 13/255,green: 20/255,blue: 28/255)
    static let card = Color(red: 24/255,green: 35/255,blue: 47/255)
    static let accent = Color(red: 97/255,green: 225/255,blue: 208/255)
}
struct CapsuleAction: ButtonStyle {
    var primary = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.padding(12).frame(minHeight: 44).frame(maxWidth: .infinity)
            .foregroundColor(primary ? Theme.bg : .white).background(primary ? Theme.accent : Theme.card)
            .clipShape(RoundedRectangle(cornerRadius: 20)).opacity(configuration.isPressed ? 0.7 : 1)
    }
}
struct RootView: View {
    @EnvironmentObject var model: CameraModel
    @Environment(\.scenePhase) var scenePhase
    @State var tab = 0
    var body: some View {
        TabView(selection: $tab) {
            GalleryView().tabItem { Label(tr("Galerie", "Gallery"),systemImage: "photo.on.rectangle") }.tag(0)
            CameraView(active: tab == 1 && scenePhase == .active).tabItem { Label(tr("Kamera", "Camera"),systemImage: "camera") }.tag(1)
            SettingsView().tabItem { Label(tr("Nastavení", "Settings"),systemImage: "gearshape") }.tag(2)
        }.accentColor(Theme.accent)
        .alert(tr("X10 Connect", "X10 Connect"),isPresented: Binding(get: { !model.notice.isEmpty },set: { if !$0 { model.notice = "" } })) { Button("OK") { model.notice = "" } } message: { Text(model.notice) }
        .onChange(of: scenePhase) { if $0 == .background { model.cancelDownloads() } }
    }
}
struct ConnectionHeader: View {
    @EnvironmentObject var model: CameraModel
    @State private var help = false
    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading,spacing: 4) {
                Text(model.connected && !model.name.isEmpty ? model.name : tr("Model kamery", "Camera model")).font(.headline)
                Text(model.connected ? tr("Připojeno", "Connected") : tr("Připojit ke kameře", "Connect to camera")).font(.caption).foregroundColor(model.connected ? Theme.accent : .secondary)
            }
            Spacer(minLength: 4)
            Text(model.battery).font(.caption)
            Button { help = true } label: { Image(systemName: "wifi").frame(width: 44,height: 44) }.accessibilityLabel(tr("Připojení Wi-Fi", "Wi-Fi connection"))
        }.padding(14).background(Theme.card).clipShape(RoundedRectangle(cornerRadius: 22))
        .alert("Wi-Fi",isPresented: $help) {
            Button(tr("Nastavení aplikace", "App settings")) { if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } }
            Button(tr("Zavřít", "Close"),role: .cancel) { }
        } message: { Text(tr("V Nastavení iPhonu ručně vyber Wi-Fi kamery a zůstaň připojený i bez internetu. Při první výzvě povol Místní síť. Tlačítko níže otevře nastavení oprávnění aplikace.", "Manually select the camera Wi-Fi in iPhone Settings and stay connected without internet. Allow Local Network when prompted. The button below opens app permission settings.")) }
    }
}
struct PageHeading: View {
    let title: String
    @EnvironmentObject var model: CameraModel
    var body: some View {
        HStack {
            Text(title).font(.title2).bold(); Spacer()
            Button { model.connect() } label: { Label(model.connected ? tr("Obnovit", "Refresh") : tr("Připojit", "Connect"),systemImage: model.connected ? "arrow.clockwise" : "cable.connector") }.font(.caption).buttonStyle(CapsuleAction()).fixedSize(horizontal: true,vertical: false).disabled(model.busy)
        }
    }
}
struct CameraView: View {
    let active: Bool
    @EnvironmentObject var model: CameraModel
    @State private var fullscreen = false
    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                ConnectionHeader(); PageHeading(title: tr("Kamera", "Camera"))
                ZStack(alignment: .bottomTrailing) {
                    if active && !fullscreen { PreviewPane(address: model.address) } else { Color.black }
                    Button { fullscreen = true } label: { Image(systemName: "arrow.up.left.and.arrow.down.right").frame(width: 44,height: 44).background(Theme.card).clipShape(Circle()) }.padding(8).accessibilityLabel(tr("Celá obrazovka", "Fullscreen"))
                }.aspectRatio(16.0/9.0,contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 20))
                VStack(alignment: .leading,spacing: 6) {
                    Text(tr("Režim kamery", "Camera mode")).font(.caption).foregroundColor(.secondary)
                    Picker(tr("Režim kamery", "Camera mode"),selection: Binding(get: { model.mode },set: { model.setMode($0) })) {
                        ForEach(model.modes,id: \.self) { Text(modeTitle($0)).tag($0) }
                    }.pickerStyle(.menu).frame(maxWidth: .infinity,minHeight: 48).background(Theme.card).clipShape(RoundedRectangle(cornerRadius: 20)).disabled(model.busy)
                }
                HStack(spacing: 8) {
                    cameraButton(tr("Vyfotit", "Photo"),"camera",true) { model.photo() }
                    cameraButton(tr("Start", "Start"),"record.circle",false) { model.recording(true) }
                    cameraButton(tr("Zastavit", "Stop"),"stop.circle",false) { model.recording(false) }
                }.disabled(model.busy)
                Text(tr("Vyber režim a potom vyfoť snímek nebo spusť záznam.", "Choose a mode, then take a photo or start recording.")).font(.caption).foregroundColor(.secondary).multilineTextAlignment(.center)
            }.padding(16)
        }.background(Theme.bg.ignoresSafeArea())
        .fullScreenCover(isPresented: $fullscreen) { ZStack(alignment: .bottomTrailing) {
            Theme.bg.ignoresSafeArea()
            if active { PreviewPane(address: model.address).ignoresSafeArea(edges: .horizontal) }
            Button(tr("Zavřít", "Close")) { fullscreen = false }.padding().background(Theme.card).clipShape(Capsule()).padding()
        } }
    }
    private func cameraButton(_ title: String,_ icon: String,_ primary: Bool,action: @escaping () -> Void) -> some View {
        Button(action: action) { VStack(spacing: 10) { Image(systemName: icon).font(.title2); Text(title).font(.caption) }.frame(maxWidth: .infinity,minHeight: 65) }.buttonStyle(CapsuleAction(primary: primary))
    }
}
struct SettingsView: View {
    @EnvironmentObject var model: CameraModel
    @AppStorage("language") private var language = "system"
    @State private var address = ""
    var body: some View {
        ScrollView { VStack(alignment: .leading,spacing: 18) {
            ConnectionHeader(); Text(tr("Nastavení", "Settings")).font(.title2).bold()
            Text(tr("Adresa kamery", "Camera address")).font(.caption)
            TextField("http://192.168.0.1",text: $address).textInputAutocapitalization(.never).disableAutocorrection(true).keyboardType(.URL).padding().background(Theme.card).cornerRadius(18)
            Button(tr("Uložit adresu", "Save address")) { model.saveAddress(address) }.buttonStyle(CapsuleAction()).disabled(model.busy || model.downloading)
            Picker("Jazyk / Language",selection: $language) { Text(tr("Podle systému", "System")).tag("system"); Text("Čeština").tag("cs"); Text("English").tag("en") }.pickerStyle(.menu)
            Button(tr("Synchronizovat čas", "Synchronize time")) { model.syncTime() }.buttonStyle(CapsuleAction()).disabled(model.busy)
            Text(tr("Stažené soubory najdeš v Soubory → Na mém iPhonu → X10 Connect → Downloads. Vybrané uložené soubory lze sdílet z Galerie. Při stahování nech aplikaci v popředí; přechod na pozadí přenos zruší.", "Downloaded files are in Files → On My iPhone → X10 Connect → Downloads. Share selected saved files from Gallery. Keep the app in the foreground while downloading; moving it to the background cancels the transfer.")).font(.footnote).foregroundColor(.secondary)
            Text("X10 Connect iOS " + (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "") + " · iOS 15+").font(.footnote)
            Text(tr("Neoficiální aplikace. X9.1 zatím není ověřená.", "Unofficial client. X9.1 is not verified yet.")).font(.caption).foregroundColor(.secondary)
            Text("VideoLAN MobileVLCKit 3.6.0 · LGPL 2.1").font(.caption)
            Link(tr("Zdrojový kód a licence", "Source and licenses"),destination: URL(string: "https://github.com/miraficus/lamax-camera-controller/tree/ios/ios")!)
        }.padding(16) }.background(Theme.bg.ignoresSafeArea()).onAppear { address = model.address }
    }
}
