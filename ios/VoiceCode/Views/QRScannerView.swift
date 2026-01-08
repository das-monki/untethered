// QRScannerView.swift
// QR code scanner for API key authentication

import SwiftUI
import AVFoundation

/// SwiftUI wrapper for the QR scanner view controller
struct QRScannerView: UIViewControllerRepresentable {
    let onCodeScanned: (String) -> Void
    let onCancel: () -> Void

    func makeUIViewController(context: Context) -> QRScannerViewController {
        LogManager.shared.log("QRScannerView: makeUIViewController called", category: "QRScanner")
        let controller = QRScannerViewController()
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: QRScannerViewController, context: Context) {
        LogManager.shared.log("QRScannerView: updateUIViewController called", category: "QRScanner")
    }

    func makeCoordinator() -> Coordinator {
        LogManager.shared.log("QRScannerView: makeCoordinator called", category: "QRScanner")
        return Coordinator(onCodeScanned: onCodeScanned, onCancel: onCancel)
    }

    class Coordinator: NSObject, QRScannerViewControllerDelegate {
        let onCodeScanned: (String) -> Void
        let onCancel: () -> Void

        init(onCodeScanned: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
            self.onCodeScanned = onCodeScanned
            self.onCancel = onCancel
        }

        func qrScannerDidScanCode(_ code: String) {
            LogManager.shared.log("Coordinator: qrScannerDidScanCode called with code length: \(code.count)", category: "QRScanner")
            onCodeScanned(code)
        }

        func qrScannerDidCancel() {
            LogManager.shared.log("Coordinator: qrScannerDidCancel called", category: "QRScanner")
            onCancel()
        }
    }
}

/// Delegate protocol for QR scanner callbacks
protocol QRScannerViewControllerDelegate: AnyObject {
    func qrScannerDidScanCode(_ code: String)
    func qrScannerDidCancel()
}

/// View controller handling camera capture and QR code detection
class QRScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    weak var delegate: QRScannerViewControllerDelegate?

    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var hasScanned = false

    // UI Elements
    private let instructionLabel = UILabel()
    private let cancelButton = UIButton(type: .system)
    private let overlayView = UIView()
    private let scannerFrame = UIView()

    override func viewDidLoad() {
        super.viewDidLoad()
        LogManager.shared.log("QRScannerViewController: viewDidLoad", category: "QRScanner")
        view.backgroundColor = .black
        setupUI()
        checkCameraPermission()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        LogManager.shared.log("QRScannerViewController: viewWillAppear", category: "QRScanner")
        hasScanned = false

        if let session = captureSession, !session.isRunning {
            LogManager.shared.log("QRScannerViewController: starting capture session", category: "QRScanner")
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.captureSession?.startRunning()
            }
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        LogManager.shared.log("QRScannerViewController: viewDidAppear", category: "QRScanner")
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        LogManager.shared.log("QRScannerViewController: viewWillDisappear", category: "QRScanner")

        if let session = captureSession, session.isRunning {
            LogManager.shared.log("QRScannerViewController: stopping capture session", category: "QRScanner")
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.captureSession?.stopRunning()
            }
        }
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        LogManager.shared.log("QRScannerViewController: viewDidDisappear", category: "QRScanner")
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
        updateScannerFrame()
    }

    // MARK: - UI Setup

    private func setupUI() {
        // Instruction label
        instructionLabel.text = "Scan the QR code from your terminal"
        instructionLabel.textColor = .white
        instructionLabel.textAlignment = .center
        instructionLabel.font = .systemFont(ofSize: 17, weight: .medium)
        instructionLabel.numberOfLines = 2
        instructionLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(instructionLabel)

        // Cancel button
        cancelButton.setTitle("Cancel", for: .normal)
        cancelButton.titleLabel?.font = .systemFont(ofSize: 17)
        cancelButton.tintColor = .white
        cancelButton.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(cancelButton)

        // Overlay with cutout for scanner
        overlayView.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        overlayView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(overlayView)

        // Scanner frame (visual border around scan area)
        scannerFrame.layer.borderColor = UIColor.white.cgColor
        scannerFrame.layer.borderWidth = 2
        scannerFrame.layer.cornerRadius = 12
        scannerFrame.backgroundColor = .clear
        scannerFrame.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scannerFrame)

        NSLayoutConstraint.activate([
            instructionLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 40),
            instructionLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            instructionLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),

            cancelButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -40),
            cancelButton.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        ])
    }

    private func updateScannerFrame() {
        let frameSize: CGFloat = min(view.bounds.width, view.bounds.height) * 0.7
        scannerFrame.frame = CGRect(
            x: (view.bounds.width - frameSize) / 2,
            y: (view.bounds.height - frameSize) / 2,
            width: frameSize,
            height: frameSize
        )

        // Update overlay with cutout
        let path = UIBezierPath(rect: view.bounds)
        let cutoutPath = UIBezierPath(roundedRect: scannerFrame.frame, cornerRadius: 12)
        path.append(cutoutPath)
        path.usesEvenOddFillRule = true

        let maskLayer = CAShapeLayer()
        maskLayer.path = path.cgPath
        maskLayer.fillRule = .evenOdd
        overlayView.layer.mask = maskLayer
        overlayView.frame = view.bounds
    }

    @objc private func cancelTapped() {
        delegate?.qrScannerDidCancel()
    }

    // MARK: - Camera Permission

    private func checkCameraPermission() {
        LogManager.shared.log("QRScannerViewController: checkCameraPermission called", category: "QRScanner")

        // Skip permission prompts during UI tests to prevent blocking automation
        if TestingEnvironment.isUITesting {
            LogManager.shared.log("QRScannerViewController: UI testing mode - showing permission denied", category: "QRScanner")
            showPermissionDenied()
            return
        }

        let status = AVCaptureDevice.authorizationStatus(for: .video)
        LogManager.shared.log("QRScannerViewController: camera authorization status = \(status.rawValue)", category: "QRScanner")

        switch status {
        case .authorized:
            LogManager.shared.log("QRScannerViewController: camera authorized, setting up camera", category: "QRScanner")
            setupCamera()
        case .notDetermined:
            LogManager.shared.log("QRScannerViewController: camera not determined, requesting access", category: "QRScanner")
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                LogManager.shared.log("QRScannerViewController: camera access response: granted=\(granted)", category: "QRScanner")
                DispatchQueue.main.async {
                    if granted {
                        self?.setupCamera()
                    } else {
                        self?.showPermissionDenied()
                    }
                }
            }
        case .denied, .restricted:
            LogManager.shared.log("QRScannerViewController: camera denied/restricted", category: "QRScanner")
            showPermissionDenied()
        @unknown default:
            LogManager.shared.log("QRScannerViewController: camera unknown status", category: "QRScanner")
            showPermissionDenied()
        }
    }

    private func showPermissionDenied() {
        // Update instruction label
        instructionLabel.text = "Camera Access Required"

        // Remove scanner frame and overlay
        scannerFrame.isHidden = true
        overlayView.isHidden = true

        // Add explanation and button
        let explanationLabel = UILabel()
        explanationLabel.text = "Please enable camera access in Settings to scan QR codes for authentication."
        explanationLabel.textColor = .lightGray
        explanationLabel.textAlignment = .center
        explanationLabel.font = .systemFont(ofSize: 15)
        explanationLabel.numberOfLines = 0
        explanationLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(explanationLabel)

        let settingsButton = UIButton(type: .system)
        settingsButton.setTitle("Open Settings", for: .normal)
        settingsButton.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        settingsButton.backgroundColor = .systemBlue
        settingsButton.tintColor = .white
        settingsButton.layer.cornerRadius = 10
        settingsButton.addTarget(self, action: #selector(openSettings), for: .touchUpInside)
        settingsButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(settingsButton)

        NSLayoutConstraint.activate([
            explanationLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -40),
            explanationLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 40),
            explanationLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -40),

            settingsButton.topAnchor.constraint(equalTo: explanationLabel.bottomAnchor, constant: 24),
            settingsButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            settingsButton.widthAnchor.constraint(equalToConstant: 160),
            settingsButton.heightAnchor.constraint(equalToConstant: 50)
        ])
    }

    @objc private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    // MARK: - Camera Setup

    private func setupCamera() {
        LogManager.shared.log("QRScannerViewController: setupCamera started", category: "QRScanner")
        let session = AVCaptureSession()

        guard let videoCaptureDevice = AVCaptureDevice.default(for: .video) else {
            LogManager.shared.log("QRScannerViewController: ERROR - No camera available", category: "QRScanner")
            showCameraError("No camera available")
            return
        }
        LogManager.shared.log("QRScannerViewController: got video capture device", category: "QRScanner")

        guard let videoInput = try? AVCaptureDeviceInput(device: videoCaptureDevice) else {
            LogManager.shared.log("QRScannerViewController: ERROR - Could not create video input", category: "QRScanner")
            showCameraError("Could not create video input")
            return
        }
        LogManager.shared.log("QRScannerViewController: created video input", category: "QRScanner")

        guard session.canAddInput(videoInput) else {
            LogManager.shared.log("QRScannerViewController: ERROR - Could not add video input", category: "QRScanner")
            showCameraError("Could not add video input")
            return
        }

        session.addInput(videoInput)
        LogManager.shared.log("QRScannerViewController: added video input to session", category: "QRScanner")

        let metadataOutput = AVCaptureMetadataOutput()

        guard session.canAddOutput(metadataOutput) else {
            LogManager.shared.log("QRScannerViewController: ERROR - Could not add metadata output", category: "QRScanner")
            showCameraError("Could not add metadata output")
            return
        }

        session.addOutput(metadataOutput)
        metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
        metadataOutput.metadataObjectTypes = [.qr]
        LogManager.shared.log("QRScannerViewController: configured metadata output for QR", category: "QRScanner")

        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.frame = view.layer.bounds
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.insertSublayer(previewLayer, at: 0)

        self.captureSession = session
        self.previewLayer = previewLayer
        LogManager.shared.log("QRScannerViewController: preview layer configured", category: "QRScanner")

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            LogManager.shared.log("QRScannerViewController: starting session.startRunning()", category: "QRScanner")
            session.startRunning()
            LogManager.shared.log("QRScannerViewController: session.startRunning() completed", category: "QRScanner")
            // Set detection rect to scanner frame area
            DispatchQueue.main.async {
                self?.updateMetadataOutputRect(metadataOutput)
            }
        }
    }

    private func updateMetadataOutputRect(_ output: AVCaptureMetadataOutput) {
        guard let previewLayer = previewLayer else { return }
        let metadataRect = previewLayer.metadataOutputRectConverted(fromLayerRect: scannerFrame.frame)
        output.rectOfInterest = metadataRect
    }

    private func showCameraError(_ message: String) {
        instructionLabel.text = "Camera Error"

        let errorLabel = UILabel()
        errorLabel.text = message
        errorLabel.textColor = .lightGray
        errorLabel.textAlignment = .center
        errorLabel.font = .systemFont(ofSize: 15)
        errorLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(errorLabel)

        NSLayoutConstraint.activate([
            errorLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            errorLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 40),
            errorLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -40)
        ])
    }

    // MARK: - AVCaptureMetadataOutputObjectsDelegate

    func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard !hasScanned else { return }

        guard let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              metadataObject.type == .qr,
              let stringValue = metadataObject.stringValue else {
            return
        }

        // Validate API key format before accepting
        guard KeychainManager.shared.isValidAPIKeyFormat(stringValue) else {
            // Show brief feedback for invalid QR code
            showInvalidQRFeedback()
            return
        }

        // Mark as scanned to prevent duplicate callbacks
        hasScanned = true

        // Stop scanning
        captureSession?.stopRunning()

        // Haptic feedback
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)

        // Notify delegate
        delegate?.qrScannerDidScanCode(stringValue)
    }

    private func showInvalidQRFeedback() {
        // Haptic feedback for error
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.error)

        // Flash the scanner frame red briefly
        let originalColor = scannerFrame.layer.borderColor
        scannerFrame.layer.borderColor = UIColor.systemRed.cgColor

        // Update instruction temporarily
        let originalText = instructionLabel.text
        instructionLabel.text = "Invalid QR code - scan API key QR"
        instructionLabel.textColor = .systemRed

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            self?.scannerFrame.layer.borderColor = originalColor
            self?.instructionLabel.text = originalText
            self?.instructionLabel.textColor = .white
        }
    }
}

// MARK: - Preview

#if DEBUG
struct QRScannerView_Previews: PreviewProvider {
    static var previews: some View {
        QRScannerView(
            onCodeScanned: { code in
                print("Scanned: \(code)")
            },
            onCancel: {
                print("Cancelled")
            }
        )
        .ignoresSafeArea()
    }
}
#endif
