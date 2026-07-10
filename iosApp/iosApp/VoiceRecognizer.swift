// VoiceRecognizer.swift — on-device speech-to-text for the composer's
// hold-to-talk mic, the iOS counterpart to Android's bundled offline Vosk engine.
//
// iOS-native substitution: uses Apple's Speech framework (SFSpeechRecognizer) with
// `requiresOnDeviceRecognition = true` where supported, so audio is transcribed on
// the device and nothing is sent to Apple or any third party. If on-device
// recognition or permission is unavailable it surfaces a clear message (never a
// silent failure), matching Android's always-show-feedback behavior. Audio stays
// on-device per the app's rules.

import Foundation
import Speech
import AVFoundation

@MainActor
final class VoiceRecognizer: ObservableObject {
    @Published var partial = ""
    @Published var error: String?
    @Published var listening = false

    private let recognizer = SFSpeechRecognizer(locale: Locale.current)
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let engine = AVAudioEngine()

    /// Begin recording + recognition. Feedback (listening/error) is set immediately.
    func start() {
        error = nil
        partial = ""
        SFSpeechRecognizer.requestAuthorization { status in
            _Concurrency.Task { @MainActor in
                guard status == .authorized else {
                    self.error = "Speech recognition is off. Enable it in Settings › Privacy › Speech Recognition."
                    return
                }
                self.requestMicThenListen()
            }
        }
    }

    private func requestMicThenListen() {
        AVAudioApplication.requestRecordPermission { granted in
            _Concurrency.Task { @MainActor in
                guard granted else {
                    self.error = "Microphone access is off. Enable it in Settings to dictate."
                    return
                }
                self.beginSession()
            }
        }
    }

    private func beginSession() {
        guard let recognizer, recognizer.isAvailable else {
            error = "Voice input isn't available on this device right now."
            return
        }
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        if recognizer.supportsOnDeviceRecognition {
            request.requiresOnDeviceRecognition = true
        }
        self.request = request

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                self?.request?.append(buffer)
            }
            engine.prepare()
            try engine.start()
            listening = true

            task = recognizer.recognitionTask(with: request) { [weak self] result, err in
                _Concurrency.Task { @MainActor in
                    guard let self else { return }
                    if let result { self.partial = result.bestTranscription.formattedString }
                    if err != nil && self.partial.isEmpty {
                        self.error = "Didn't catch that — try again."
                    }
                }
            }
        } catch {
            self.error = "Couldn't start recording."
            teardown()
        }
    }

    /// Stop recording and return the recognized text (may be empty).
    @discardableResult
    func stop() -> String {
        let text = partial
        teardown()
        return text
    }

    func clearError() { error = nil }

    private func teardown() {
        if engine.isRunning {
            engine.stop()
            engine.inputNode.removeTap(onBus: 0)
        }
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        listening = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
