import XCTest
@testable import CameraCore
final class ProtocolTests: XCTestCase {
    func testCameraResponseVariables() {
        let v = CameraProtocol.variables("var name=\"LAMAX X10.1\";\r\nvar model=\"3189112\";var value=\"\";")
        XCTAssertEqual(v["name"], "LAMAX X10.1"); XCTAssertEqual(v["value"], "")
    }
    func testRejectsUnsafeAddresses() {
        for address in ["https://192.168.0.1", "http://example.com", "http://192.168.0.1/cgi-bin", "http://user@192.168.0.1", "http://192.168.0.1?x=1", "http://192.168.999.1", "http://8.8.8.8", "http://010.0.0.1"] { XCTAssertThrowsError(try CameraProtocol.base(address)) }
        XCTAssertEqual(try CameraProtocol.base("192.168.0.1").host, "192.168.0.1")
    }
    func testPathsCannotEscapeMediaDirectory() throws {
        for path in ["sd/DCIM/x/../secret.JPG", "sd/DCIM/x/%2e.JPG", "sd/DCIM/x/a.JPG?x=1", "sd/DCIM//a.JPG", "res/main_app", "sd/DCIM/x/a.cgi", "sd/DCIM/x/a.JPG#x"] { XCTAssertThrowsError(try CameraProtocol.safePath(path)) }
        XCTAssertEqual(try CameraProtocol.safePath("sd/DCIM/100GKUDV/SING0076.JPG"), "sd/DCIM/100GKUDV/SING0076.JPG")
    }
    func testFileListDedupAndMetadata() throws {
        let row = #"{"path":"sd/DCIM/100GKUDV/SING0076.JPG","create":"2026/05/22 13:56:58","time":"0","size":"2966788"}"#
        let files = try CameraProtocol.files(Data(("[" + row + "," + row + "]").utf8))
        XCTAssertEqual(files.count, 1); XCTAssertEqual(files.first?.bytes, 2966788)
        XCTAssertEqual(files.first?.thumbnail, "sd/DCIM/100GKUDV/SING0076.THM")
    }
    func testEncodedSettingsAndObservedVideoLapseStart() throws {
        let url = CameraProtocol.command(try CameraProtocol.base("192.168.0.1"), "setcurparameter.cgi", [("-name", "Lens angle"), ("-value", "Wide")])
        XCTAssertEqual(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value, "Lens angle")
        XCTAssertEqual(CameraProtocol.captureCommand(mode: "VideoLapse", start: true).0, "photo.cgi")
        XCTAssertEqual(CameraProtocol.captureCommand(mode: "NormalVideo", start: true).1.first?.1, "start")
        XCTAssertEqual(CameraProtocol.captureCommand(mode: "VideoLapse", start: false).1.first?.1, "stop")
    }
}
