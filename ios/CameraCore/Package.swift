// swift-tools-version: 5.7
import PackageDescription
let package = Package(name: "CameraCore", platforms: [.iOS(.v15), .macOS(.v12)], products: [.library(name: "CameraCore", targets: ["CameraCore"])], targets: [.target(name: "CameraCore"), .testTarget(name: "CameraCoreTests", dependencies: ["CameraCore"])])
