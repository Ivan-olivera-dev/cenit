import AppKit
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers
import WebKit

struct HtmlJob: Decodable {
    let htmlPath: String
    let outPath: String
    let width: Int
    let height: Int
    let transparent: Bool
}

struct PhoneFrame: Decodable {
    let imagePath: String
    let x: Double
    let y: Double
    let width: Double
    let height: Double
    let radius: Double
    let borderColor: String
    let borderWidth: Double
}

struct ShowcaseJob: Decodable {
    let outPath: String
    let width: Int
    let height: Int
    let background: String
    let phones: [PhoneFrame]
}

struct Manifest: Decodable {
    let readAccessRoot: String
    let htmlJobs: [HtmlJob]
    let showcaseJobs: [ShowcaseJob]
}

func fail(_ message: String) -> Never {
    fputs("rasterize.swift error: \(message)\n", stderr)
    exit(1)
}

func parseHexColor(_ hex: String) -> CGColor {
    let cleaned = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
    guard cleaned.count == 6, let value = UInt32(cleaned, radix: 16) else {
        fail("invalid hex color: \(hex)")
    }
    let r = CGFloat((value >> 16) & 0xFF) / 255.0
    let g = CGFloat((value >> 8) & 0xFF) / 255.0
    let b = CGFloat(value & 0xFF) / 255.0
    let cs = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
    return CGColor(colorSpace: cs, components: [r, g, b, 1.0])!
}

func savePNG(_ cgImage: CGImage, to outPath: String) {
    let outURL = URL(fileURLWithPath: outPath)
    guard let dest = CGImageDestinationCreateWithURL(
        outURL as CFURL,
        UTType.png.identifier as CFString,
        1,
        nil
    ) else {
        fail("could not create PNG destination for \(outPath)")
    }
    CGImageDestinationAddImage(dest, cgImage, nil)
    if !CGImageDestinationFinalize(dest) {
        fail("could not finalize PNG \(outPath)")
    }
}

func renderShowcase(_ job: ShowcaseJob) {
    let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
    guard let ctx = CGContext(
        data: nil,
        width: job.width,
        height: job.height,
        bitsPerComponent: 8,
        bytesPerRow: job.width * 4,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    ) else {
        fail("could not allocate CGContext for \(job.outPath)")
    }

    ctx.interpolationQuality = .high
    ctx.setAllowsAntialiasing(true)
    ctx.setShouldAntialias(true)

    // Fill paper background.
    ctx.setFillColor(parseHexColor(job.background))
    ctx.fill(CGRect(x: 0, y: 0, width: job.width, height: job.height))

    // Draw each framed screenshot (convert top-left y to CoreGraphics bottom-left y).
    for phone in job.phones {
        let imgURL = URL(fileURLWithPath: phone.imagePath)
        guard let src = CGImageSourceCreateWithURL(imgURL as CFURL, nil),
              let cgImg = CGImageSourceCreateImageAtIndex(src, 0, nil) else {
            fail("could not load screenshot \(phone.imagePath)")
        }

        let cgY = Double(job.height) - phone.y - phone.height
        let rect = CGRect(x: phone.x, y: cgY, width: phone.width, height: phone.height)
        let clipPath = CGPath(
            roundedRect: rect,
            cornerWidth: phone.radius,
            cornerHeight: phone.radius,
            transform: nil
        )

        ctx.saveGState()
        ctx.addPath(clipPath)
        ctx.clip()
        ctx.draw(cgImg, in: rect)
        ctx.restoreGState()

        // Crisp hairline border on top of the rounded frame (no heavy shadow per design system).
        if phone.borderWidth > 0 {
            let inset = phone.borderWidth / 2.0
            let strokeRect = rect.insetBy(dx: inset, dy: inset)
            let strokeRadius = max(0, phone.radius - inset)
            let strokePath = CGPath(
                roundedRect: strokeRect,
                cornerWidth: strokeRadius,
                cornerHeight: strokeRadius,
                transform: nil
            )
            ctx.saveGState()
            ctx.setStrokeColor(parseHexColor(phone.borderColor))
            ctx.setLineWidth(phone.borderWidth)
            ctx.addPath(strokePath)
            ctx.strokePath()
            ctx.restoreGState()
        }
    }

    guard let outCG = ctx.makeImage() else {
        fail("could not create output CGImage for \(job.outPath)")
    }
    savePNG(outCG, to: job.outPath)
}

final class BatchRenderer: NSObject, WKNavigationDelegate {
    private let readAccessURL: URL
    private var queue: [HtmlJob]
    private var currentJob: HtmlJob?
    private var webView: WKWebView?
    private var window: NSWindow?

    init(manifest: Manifest) {
        self.readAccessURL = URL(fileURLWithPath: manifest.readAccessRoot, isDirectory: true)
        self.queue = manifest.htmlJobs
        super.init()
    }

    func start() {
        processNext()
    }

    private func processNext() {
        guard !queue.isEmpty else {
            exit(0)
        }
        let job = queue.removeFirst()
        currentJob = job

        let rect = NSRect(x: 0, y: 0, width: job.width, height: job.height)
        let config = WKWebViewConfiguration()

        let wv = WKWebView(frame: rect, configuration: config)
        wv.navigationDelegate = self
        wv.setValue(!job.transparent, forKey: "drawsBackground")

        let win = NSWindow(
            contentRect: rect,
            styleMask: [.borderless],
            backing: .buffered,
            defer: false
        )
        win.isOpaque = !job.transparent
        win.backgroundColor = job.transparent ? .clear : .black
        win.contentView = wv

        self.webView = wv
        self.window = win

        let htmlURL = URL(fileURLWithPath: job.htmlPath)
        wv.loadFileURL(htmlURL, allowingReadAccessTo: readAccessURL)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        fail("navigation failed for \(currentJob?.outPath ?? "?"): \(error)")
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        fail("provisional navigation failed for \(currentJob?.outPath ?? "?"): \(error)")
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        captureCurrent()
    }

    private func captureCurrent() {
        guard let job = currentJob, let wv = webView else { return }

        let snapConfig = WKSnapshotConfiguration()
        snapConfig.rect = CGRect(x: 0, y: 0, width: job.width, height: job.height)
        snapConfig.snapshotWidth = NSNumber(value: job.width)
        snapConfig.afterScreenUpdates = true

        wv.takeSnapshot(with: snapConfig) { [weak self] image, error in
            guard let self else { return }
            if let error {
                fail("snapshot failed for \(job.outPath): \(error)")
            }
            guard let image else {
                fail("snapshot returned nil image for \(job.outPath)")
            }
            self.writePNG(image: image, job: job)
            self.webView = nil
            self.window = nil
            self.processNext()
        }
    }

    private func writePNG(image: NSImage, job: HtmlJob) {
        let width = job.width
        let height = job.height
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        let bitmapInfo: UInt32 = job.transparent
            ? CGImageAlphaInfo.premultipliedLast.rawValue
            : CGImageAlphaInfo.noneSkipLast.rawValue

        guard let ctx = CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: bitmapInfo
        ) else {
            fail("could not allocate CGContext for \(job.outPath)")
        }

        ctx.interpolationQuality = .high
        ctx.setAllowsAntialiasing(true)
        ctx.setShouldAntialias(true)

        var proposedRect = NSRect(x: 0, y: 0, width: width, height: height)
        guard let cgSource = image.cgImage(forProposedRect: &proposedRect, context: nil, hints: nil) else {
            fail("could not extract CGImage for \(job.outPath)")
        }

        ctx.draw(cgSource, in: CGRect(x: 0, y: 0, width: width, height: height))

        guard let outCG = ctx.makeImage() else {
            fail("could not create output CGImage for \(job.outPath)")
        }
        savePNG(outCG, to: job.outPath)
    }
}

guard CommandLine.arguments.count == 2 else {
    fputs("Usage: swift rasterize.swift <manifest.json>\n", stderr)
    exit(2)
}

let manifestURL = URL(fileURLWithPath: CommandLine.arguments[1])
let data = try Data(contentsOf: manifestURL)
let manifest = try JSONDecoder().decode(Manifest.self, from: data)

// Composite showcase images synchronously via CoreGraphics.
for job in manifest.showcaseJobs {
    renderShowcase(job)
}

if manifest.htmlJobs.isEmpty {
    exit(0)
}

let app = NSApplication.shared
app.setActivationPolicy(.prohibited)

let renderer = BatchRenderer(manifest: manifest)
DispatchQueue.main.async {
    renderer.start()
}
app.run()
