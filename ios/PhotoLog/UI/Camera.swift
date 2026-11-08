import SwiftUI
import AVFoundation

/**
 * PHOTO-LOG 촬영 화면 (iPhone · iPad · 맥 공용, AVFoundation).
 * - 좌우 반전: 미리보기와 저장되는 사진이 똑같이 거울처럼 뒤집힌다(보이는 그대로 저장).
 *   기본값은 전면(셀카)·맥 카메라는 켜짐, 후면 카메라는 꺼짐. 바꾼 값은 카메라별로 기억한다.
 * - iPhone·iPad: 앞·뒤 카메라 전환, 플래시, 두 손가락 확대
 * - 맥: 내장 카메라(또는 연결된 카메라), 스페이스바로 촬영
 */
final class CameraController: NSObject, ObservableObject, AVCapturePhotoCaptureDelegate {
    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private let queue = DispatchQueue(label: "photolog.camera")
    private var input: AVCaptureDeviceInput?
    private var zoomStart: CGFloat = 1
    weak var previewLayer: AVCaptureVideoPreviewLayer?

    @Published var ready = false
    @Published var failed: String?
    @Published var shooting = false
    @Published var position: AVCaptureDevice.Position = .back
    @Published var mirrored = false
    @Published var flash: AVCaptureDevice.FlashMode = .off
    @Published var hasFlash = false
    @Published var canSwitch = false
    var onPhoto: ((UIImage, [String: Any]) -> Void)?

    static var available: Bool { AVCaptureDevice.default(for: .video) != nil }

    static var isMac: Bool {
        #if targetEnvironment(macCatalyst)
        return true
        #else
        return false
        #endif
    }

    // MARK: - 시작·권한

    func start() {
        #if !targetEnvironment(macCatalyst)
        UIDevice.current.beginGeneratingDeviceOrientationNotifications()
        #endif
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configure(position)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.configure(self.position) } else { self.failed = Self.deniedMessage }
                }
            }
        default:
            failed = Self.deniedMessage
        }
    }

    func stop() {
        queue.async { if self.session.isRunning { self.session.stopRunning() } }
        #if !targetEnvironment(macCatalyst)
        UIDevice.current.endGeneratingDeviceOrientationNotifications()
        #endif
    }

    var denied: Bool {
        let s = AVCaptureDevice.authorizationStatus(for: .video)
        return s == .denied || s == .restricted
    }

    static var deniedMessage: String {
        isMac
            ? "카메라 권한이 없습니다.\n시스템 설정 → 개인정보 보호 및 보안 → 카메라에서 PHOTO-LOG를 켜 주세요."
            : "카메라 권한이 없습니다.\n설정 앱 → PHOTO-LOG → 카메라를 켜 주세요."
    }

    static var settingsURL: URL? {
        isMac
            ? URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Camera")
            : URL(string: UIApplication.openSettingsURLString)
    }

    // MARK: - 카메라 구성

    private static func device(_ position: AVCaptureDevice.Position) -> AVCaptureDevice? {
        if isMac || position == .unspecified { return AVCaptureDevice.default(for: .video) }
        return AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position)
            ?? AVCaptureDevice.default(for: .video)
    }

    private static func mirrorKey(_ position: AVCaptureDevice.Position) -> String {
        isMac ? "mirror.mac" : (position == .front ? "mirror.front" : "mirror.back")
    }

    private static func savedMirror(_ position: AVCaptureDevice.Position) -> Bool {
        let key = mirrorKey(position)
        if UserDefaults.standard.object(forKey: key) == nil { return isMac || position == .front }
        return UserDefaults.standard.bool(forKey: key)
    }

    private func configure(_ wanted: AVCaptureDevice.Position) {
        queue.async {
            guard let device = Self.device(wanted), let newInput = try? AVCaptureDeviceInput(device: device) else {
                DispatchQueue.main.async { self.failed = "카메라를 찾을 수 없습니다" }
                return
            }
            self.session.beginConfiguration()
            self.session.sessionPreset = .photo
            if let old = self.input { self.session.removeInput(old) }
            guard self.session.canAddInput(newInput) else {
                if let old = self.input { self.session.addInput(old) }
                self.session.commitConfiguration()
                DispatchQueue.main.async { self.failed = "카메라를 열 수 없습니다" }
                return
            }
            self.session.addInput(newInput)
            self.input = newInput
            if !self.session.outputs.contains(self.output), self.session.canAddOutput(self.output) {
                self.session.addOutput(self.output)
            }
            self.session.commitConfiguration()
            if !self.session.isRunning { self.session.startRunning() }

            let pos = device.position
            let flashOK = !Self.isMac && device.hasFlash
            let switchOK = !Self.isMac
                && Self.device(.front) != nil
                && AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil
            DispatchQueue.main.async {
                self.position = pos
                self.mirrored = Self.savedMirror(pos)
                self.hasFlash = flashOK
                self.canSwitch = switchOK
                self.failed = nil
                self.ready = true
                self.applyPreview()
            }
        }
    }

    func switchCamera() {
        guard canSwitch, !shooting else { return }
        ready = false
        configure(position == .front ? .back : .front)
    }

    func toggleMirror() {
        mirrored.toggle()
        UserDefaults.standard.set(mirrored, forKey: Self.mirrorKey(position))
        applyPreview()
    }

    func cycleFlash() {
        flash = flash == .off ? .on : (flash == .on ? .auto : .off)
    }

    /// 미리보기의 좌우 반전·방향을 지금 설정에 맞춘다.
    func applyPreview() {
        guard let c = previewLayer?.connection else { return }
        if c.isVideoMirroringSupported {
            c.automaticallyAdjustsVideoMirroring = false
            c.isVideoMirrored = mirrored
        }
        #if !targetEnvironment(macCatalyst)
        if c.isVideoOrientationSupported, let o = Self.interfaceVideoOrientation() {
            c.videoOrientation = o
        }
        #endif
    }

    // MARK: - 확대

    func zoomBegan() {
        zoomStart = input?.device.videoZoomFactor ?? 1
    }

    func zoom(_ scale: CGFloat) {
        guard let device = input?.device else { return }
        let maxZ = min(device.activeFormat.videoMaxZoomFactor, 6)
        let z = min(max(zoomStart * scale, 1), maxZ)
        queue.async {
            guard (try? device.lockForConfiguration()) != nil else { return }
            device.videoZoomFactor = z
            device.unlockForConfiguration()
        }
    }

    // MARK: - 촬영

    func shoot() {
        guard ready, !shooting else { return }
        shooting = true
        let settings = output.availablePhotoCodecTypes.contains(.jpeg)
            ? AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.jpeg])
            : AVCapturePhotoSettings()
        if hasFlash, output.supportedFlashModes.contains(flash) { settings.flashMode = flash }
        if let c = output.connection(with: .video) {
            if c.isVideoMirroringSupported {
                c.automaticallyAdjustsVideoMirroring = false
                c.isVideoMirrored = mirrored
            }
            #if !targetEnvironment(macCatalyst)
            if c.isVideoOrientationSupported, let o = Self.captureVideoOrientation() {
                c.videoOrientation = o
            }
            #endif
        }
        output.capturePhoto(with: settings, delegate: self)
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let data = photo.fileDataRepresentation()
        let meta = photo.metadata
        DispatchQueue.main.async {
            self.shooting = false
            guard error == nil, let data = data, let img = UIImage(data: data) else {
                self.failed = "사진을 찍지 못했습니다"
                return
            }
            self.onPhoto?(img, meta)
        }
    }

    // MARK: - 방향 (iPhone·iPad)

    #if !targetEnvironment(macCatalyst)
    static func interfaceVideoOrientation() -> AVCaptureVideoOrientation? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        switch scene?.interfaceOrientation {
        case .portrait: return .portrait
        case .portraitUpsideDown: return .portraitUpsideDown
        case .landscapeLeft: return .landscapeLeft
        case .landscapeRight: return .landscapeRight
        default: return nil
        }
    }

    /// 화면이 세로로 고정돼 있어도 폰을 눕혀 찍으면 가로 사진이 되도록 기기 방향을 따른다.
    static func captureVideoOrientation() -> AVCaptureVideoOrientation? {
        switch UIDevice.current.orientation {
        case .portrait: return .portrait
        case .portraitUpsideDown: return .portraitUpsideDown
        case .landscapeLeft: return .landscapeRight
        case .landscapeRight: return .landscapeLeft
        default: return interfaceVideoOrientation()
        }
    }
    #endif
}

private struct CameraPreview: UIViewRepresentable {
    let cam: CameraController

    final class PreviewView: UIView {
        weak var cam: CameraController?
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }

        override func layoutSubviews() {
            super.layoutSubviews()
            cam?.applyPreview() // iPad를 돌렸을 때 미리보기 방향을 맞춘다
        }
    }

    func makeUIView(context: Context) -> PreviewView {
        let v = PreviewView()
        v.backgroundColor = .black
        v.cam = cam
        v.previewLayer.session = cam.session
        v.previewLayer.videoGravity = CameraController.isMac ? .resizeAspect : .resizeAspectFill
        cam.previewLayer = v.previewLayer
        return v
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}
}

struct CameraView: View {
    @Binding var isPresented: Bool
    let onCapture: (UIImage, [String: Any]) -> Void
    @StateObject private var cam = CameraController()
    @State private var flashOverlay = false
    @State private var pinching = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            CameraPreview(cam: cam)
                .ignoresSafeArea()
                .gesture(
                    MagnificationGesture()
                        .onChanged { m in
                            if !pinching { pinching = true; cam.zoomBegan() }
                            cam.zoom(m)
                        }
                        .onEnded { _ in pinching = false }
                )
            Color.white.opacity(flashOverlay ? 0.8 : 0).ignoresSafeArea().allowsHitTesting(false)

            if let f = cam.failed {
                VStack(spacing: 18) {
                    Text(f)
                        .font(.system(size: 15))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineSpacing(6)
                    if cam.denied, let u = CameraController.settingsURL {
                        Button(CameraController.isMac ? "시스템 설정 열기" : "설정 열기") {
                            UIApplication.shared.open(u)
                        }
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(.black)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 9)
                        .background(Color.white, in: Capsule())
                    }
                }
                .padding(24)
            } else if !cam.ready {
                ProgressView().tint(.white)
            }

            VStack(spacing: 0) {
                topBar
                Spacer()
                bottomBar
            }
        }
        .statusBarHidden()
        .onAppear {
            LocationProvider.shared.refresh()
            cam.onPhoto = { img, meta in
                onCapture(img, meta)
                isPresented = false
            }
            cam.start()
        }
        .onDisappear { cam.stop() }
    }

    private var topBar: some View {
        HStack {
            if cam.hasFlash {
                Button { cam.cycleFlash() } label: {
                    Image(systemName: cam.flash == .off ? "bolt.slash.fill" : (cam.flash == .on ? "bolt.fill" : "bolt.badge.a.fill"))
                        .font(.system(size: 17, weight: .medium))
                        .foregroundColor(cam.flash == .off ? .white : Color(Palette.filmOrange))
                        .frame(width: 40, height: 40)
                        .background(Color.black.opacity(0.4), in: Circle())
                }
                .accessibilityLabel(cam.flash == .off ? "플래시 끔" : (cam.flash == .on ? "플래시 켬" : "플래시 자동"))
            }
            Spacer()
            Button("닫기") { isPresented = false }
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Color.black.opacity(0.45), in: Capsule())
                .keyboardShortcut(.cancelAction)
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
    }

    private var bottomBar: some View {
        VStack(spacing: 10) {
            HStack {
                // 좌우 반전
                Button { cam.toggleMirror() } label: {
                    VStack(spacing: 4) {
                        Image(systemName: "arrow.left.and.right.righttriangle.left.righttriangle.right")
                            .font(.system(size: 18, weight: .medium))
                        Text("반전").font(.system(size: 11, weight: .medium))
                    }
                    .foregroundColor(cam.mirrored ? Color(Palette.filmOrange) : .white)
                    .frame(width: 64, height: 56)
                    .background(Color.black.opacity(0.4), in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
                .disabled(!cam.ready)
                .accessibilityLabel(cam.mirrored ? "좌우 반전 켬" : "좌우 반전 끔")

                Spacer()

                Button {
                    withAnimation(.easeOut(duration: 0.08)) { flashOverlay = true }
                    withAnimation(.easeIn(duration: 0.25).delay(0.08)) { flashOverlay = false }
                    cam.shoot()
                } label: {
                    ZStack {
                        Circle().strokeBorder(Color.white, lineWidth: 4).frame(width: 74, height: 74)
                        Circle().fill(Color.white).frame(width: 60, height: 60)
                    }
                }
                .buttonStyle(.plain)
                .keyboardShortcut(.space, modifiers: [])
                .disabled(!cam.ready || cam.shooting)
                .opacity(cam.ready ? 1 : 0.4)
                .accessibilityLabel("사진 찍기")

                Spacer()

                // 앞·뒤 카메라 전환 (iPhone·iPad)
                if cam.canSwitch {
                    Button { cam.switchCamera() } label: {
                        VStack(spacing: 4) {
                            Image(systemName: "arrow.triangle.2.circlepath.camera")
                                .font(.system(size: 18, weight: .medium))
                            Text("전환").font(.system(size: 11, weight: .medium))
                        }
                        .foregroundColor(.white)
                        .frame(width: 64, height: 56)
                        .background(Color.black.opacity(0.4), in: RoundedRectangle(cornerRadius: 14))
                    }
                    .buttonStyle(.plain)
                    .disabled(!cam.ready || cam.shooting)
                    .accessibilityLabel("앞·뒤 카메라 전환")
                } else {
                    Color.clear.frame(width: 64, height: 56)
                }
            }
            .frame(maxWidth: 420)
            if CameraController.isMac {
                Text("스페이스바로도 찍을 수 있습니다")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.7))
            }
        }
        .padding(.horizontal, 28)
        .padding(.bottom, 28)
    }
}
