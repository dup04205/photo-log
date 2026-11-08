import SwiftUI
import AVFoundation

/// 맥북 내장 카메라(또는 연결된 카메라)로 찍는 화면.
/// iPhone의 카메라 화면(UIImagePickerController)은 맥에서 쓸 수 없어서 AVFoundation으로 직접 만든다.
final class CameraController: NSObject, ObservableObject, AVCapturePhotoCaptureDelegate {
    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private let queue = DispatchQueue(label: "photolog.camera")
    @Published var ready = false
    @Published var failed: String?
    @Published var shooting = false
    var onPhoto: ((UIImage, [String: Any]) -> Void)?

    static var available: Bool { AVCaptureDevice.default(for: .video) != nil }

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async {
                    if ok { self.configure() } else { self.failed = Self.deniedMessage }
                }
            }
        default:
            failed = Self.deniedMessage
        }
    }

    private static let deniedMessage = "카메라 권한이 없습니다.\n시스템 설정 → 개인정보 보호 및 보안 → 카메라에서 PHOTO-LOG를 허용해 주세요."

    private func configure() {
        queue.async {
            guard !self.session.isRunning else { return }
            self.session.beginConfiguration()
            self.session.sessionPreset = .photo
            guard let device = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: device),
                  self.session.canAddInput(input), self.session.canAddOutput(self.output)
            else {
                self.session.commitConfiguration()
                DispatchQueue.main.async { self.failed = "카메라를 찾을 수 없습니다" }
                return
            }
            self.session.addInput(input)
            self.session.addOutput(self.output)
            self.session.commitConfiguration()
            self.session.startRunning()
            DispatchQueue.main.async { self.ready = true }
        }
    }

    func stop() {
        queue.async { if self.session.isRunning { self.session.stopRunning() } }
    }

    func shoot() {
        guard ready, !shooting else { return }
        shooting = true
        let settings = output.availablePhotoCodecTypes.contains(.jpeg)
            ? AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.jpeg])
            : AVCapturePhotoSettings()
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
}

private struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    func makeUIView(context: Context) -> PreviewView {
        let v = PreviewView()
        v.backgroundColor = .black
        v.previewLayer.session = session
        v.previewLayer.videoGravity = .resizeAspect
        return v
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}
}

struct MacCameraView: View {
    @Binding var isPresented: Bool
    let onCapture: (UIImage, [String: Any]) -> Void
    @StateObject private var cam = CameraController()
    @State private var flash = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            CameraPreview(session: cam.session)
                .ignoresSafeArea()
            Color.white.opacity(flash ? 0.8 : 0).ignoresSafeArea().allowsHitTesting(false)

            if let f = cam.failed {
                Text(f)
                    .font(.system(size: 15))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
                    .padding(24)
            } else if !cam.ready {
                ProgressView().tint(.white)
            }

            VStack {
                HStack {
                    Spacer()
                    Button("닫기") { isPresented = false }
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.45), in: Capsule())
                        .keyboardShortcut(.cancelAction)
                }
                .padding(20)
                Spacer()
                Button {
                    withAnimation(.easeOut(duration: 0.08)) { flash = true }
                    withAnimation(.easeIn(duration: 0.25).delay(0.08)) { flash = false }
                    cam.shoot()
                } label: {
                    ZStack {
                        Circle().strokeBorder(Color.white, lineWidth: 4).frame(width: 72, height: 72)
                        Circle().fill(Color.white).frame(width: 58, height: 58)
                    }
                }
                .buttonStyle(.plain)
                .keyboardShortcut(.space, modifiers: [])
                .disabled(!cam.ready || cam.shooting)
                .opacity(cam.ready ? 1 : 0.4)
                .accessibilityLabel("사진 찍기")
                Text("스페이스바로도 찍을 수 있습니다")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.7))
                    .padding(.top, 8)
                    .padding(.bottom, 28)
            }
        }
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
}
