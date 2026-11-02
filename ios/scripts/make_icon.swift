import AppKit
import Foundation
let root = URL(fileURLWithPath: CommandLine.arguments[1])
let folder = root.appendingPathComponent("AppIcon.appiconset")
try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
let specs: [(String,String,String,Int)] = [
    ("iphone","20x20","2x",40),("iphone","20x20","3x",60),
    ("iphone","29x29","2x",58),("iphone","29x29","3x",87),
    ("iphone","40x40","2x",80),("iphone","40x40","3x",120),
    ("iphone","60x60","2x",120),("iphone","60x60","3x",180),
    ("ipad","20x20","1x",20),("ipad","20x20","2x",40),
    ("ipad","29x29","1x",29),("ipad","29x29","2x",58),
    ("ipad","40x40","1x",40),("ipad","40x40","2x",80),
    ("ipad","76x76","1x",76),("ipad","76x76","2x",152),
    ("ipad","83.5x83.5","2x",167),("ios-marketing","1024x1024","1x",1024)]
var images: [[String:String]] = []
for (index,spec) in specs.enumerated() {
    let (idiom,size,scale,pixels) = spec
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: pixels, pixelsHigh: pixels, bitsPerSample: 8, samplesPerPixel: 3, hasAlpha: false, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    let c = NSGraphicsContext.current!.cgContext;c.scaleBy(x: CGFloat(pixels)/1024, y: CGFloat(pixels)/1024)
    c.setFillColor(NSColor(srgbRed: 13/255,green: 20/255,blue: 28/255,alpha: 1).cgColor);c.fill(CGRect(x: 0,y: 0,width: 1024,height: 1024))
    c.setStrokeColor(NSColor(srgbRed: 97/255,green: 225/255,blue: 208/255,alpha: 1).cgColor);c.setLineWidth(40)
    c.addPath(CGPath(roundedRect: CGRect(x: 190,y: 270,width: 644,height: 470),cornerWidth: 90,cornerHeight: 90,transform: nil));c.strokePath()
    c.strokeEllipse(in: CGRect(x: 365,y: 355,width: 294,height: 294))
    c.setLineJoin(.round);c.move(to: CGPoint(x: 350,y: 740));c.addLine(to: CGPoint(x: 400,y: 810));c.addLine(to: CGPoint(x: 624,y: 810));c.addLine(to: CGPoint(x: 674,y: 740));c.strokePath()
    NSGraphicsContext.restoreGraphicsState()
    let filename = "icon\(index).png";try rep.representation(using: .png, properties: [:])!.write(to: folder.appendingPathComponent(filename))
    images.append(["idiom":idiom,"size":size,"scale":scale,"filename":filename])
}
try JSONSerialization.data(withJSONObject: ["images":images,"info":["version":1,"author":"xcode"]],options: .prettyPrinted).write(to: folder.appendingPathComponent("Contents.json"))
try JSONSerialization.data(withJSONObject: ["info":["version":1,"author":"xcode"]]).write(to: root.appendingPathComponent("Contents.json"))
