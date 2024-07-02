package com.example.soundhounddemo.fxml.controllers;

import com.Hound.HoundRequester.*;
import com.Hound.HoundJSON.*;
import com.example.soundhounddemo.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import javax.sound.sampled.*;
import java.io.*;
import java.util.UUID;

public class HomePageController {

    @FXML
    private TextField inputTextField;

    @FXML
    private ListView<Message> messageListView;

    @FXML
    private Button recordingButton;

    @FXML
    private CheckBox isConversationContextActive;

    @FXML
    private ComboBox<String> microphoneComboBox;

    private boolean recording = false;
    private HoundCloudRequester requester;

    private ByteArrayOutputStream out;
    private AudioFormat format;
    private TargetDataLine microphone;
    private Thread recordingThread;

    @FXML
    public void initialize() {
        // Initialize the HoundCloudRequester with credentials from SessionManager
        SessionManager session = SessionManager.getInstance();
        requester = new HoundCloudRequester(
                session.getClientId(),
                session.getClientKey(),
                session.getUsername(),
                "https://api.houndify.com/v1/text",
                "https://api.houndify.com/v1/audio"
        );

        messageListView.setCellFactory(new Callback<ListView<Message>, ListCell<Message>>() {
            @Override
            public ListCell<Message> call(ListView<Message> param) {
                return new MessageListCell();
            }
        });

        // Setup audio format
        format = new AudioFormat(16000, 16, 1, true, true);

        // Populate the microphone ComboBox
        populateMicrophoneComboBox();
    }

    private void populateMicrophoneComboBox() {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] lineInfos = mixer.getTargetLineInfo();
            for (Line.Info lineInfo : lineInfos) {
                if (lineInfo instanceof DataLine.Info) {
                    DataLine.Info dataLineInfo = (DataLine.Info) lineInfo;
                    if (AudioSystem.isLineSupported(dataLineInfo)) {
                        microphoneComboBox.getItems().add(mixerInfo.getName());
                    }
                }
            }
        }
    }

    @FXML
    private void handleSendTextRequest() {
        String text = inputTextField.getText();
        if (text == null || text.isEmpty()) {
            addMessage(new Message(SessionManager.getInstance().getUsername(), "Please enter text to send."));
            return;
        }

        addMessage(new Message(SessionManager.getInstance().getUsername(), text));

        try {
            // Create request info
            RequestInfoJSON requestInfo = new RequestInfoJSON();
            requestInfo.setUnitPreference(RequestInfoJSON.TypeUnitPreference.UnitPreference_US);
            requestInfo.setRequestID(UUID.randomUUID().toString());
            requestInfo.setSessionID(UUID.randomUUID().toString());
            RequestInfoJSON.TypeClientVersion clientVersion = new RequestInfoJSON.TypeClientVersion();
            clientVersion.key = 0;
            clientVersion.choice0 = "1.0";
            requestInfo.setClientVersion(clientVersion);

            // Perform text request
            ConversationStateJSON conversationState = isConversationContextActive.isSelected()
                    ? SessionManager.getInstance().getConversationState()
                    : null;

            HoundServerJSON houndResult = requester.do_text_request(text, conversationState, requestInfo);

            // Log the full result for debugging
            System.out.println("Hound Result: " + houndResult);

            // Check the result
            if (houndResult != null && houndResult.hasAllResults()) {
                if (houndResult.countOfAllResults() > 0) {
                    String response = houndResult.elementOfAllResults(0).getWrittenResponseLong();
                    addMessage(new Message("Sound Hound", response));

                    // Save the conversation state
                    if (isConversationContextActive.isSelected()) {
                        SessionManager.getInstance().setConversationState(houndResult.elementOfAllResults(0).getConversationState());
                    }
                } else {
                    addMessage(new Message("Sound Hound", "No match."));
                }
            } else if (houndResult != null && houndResult.hasErrorMessage()) {
                addMessage(new Message("Sound Hound", "Error: " + houndResult.getErrorMessage()));
            } else {
                addMessage(new Message("Sound Hound", "No result or error from server."));
            }
        } catch (Exception e) {
            addMessage(new Message("Sound Hound", "Error: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void addMessage(Message message) {
        messageListView.getItems().add(message);
    }

    @FXML
    private void handleRecordButton() {
        if (recording) {
            // Stop recording
            stopRecording();
            recordingButton.setStyle(""); // Reset button style
            recording = false;
        } else {
            // Start recording
            startRecording();
            recordingButton.setStyle("-fx-background-color: red;");
            recording = true;
        }
    }

    private void startRecording() {
        out = new ByteArrayOutputStream();
        try {
            String selectedMicrophone = microphoneComboBox.getSelectionModel().getSelectedItem();
            if (selectedMicrophone == null) {
                addMessage(new Message("Sound Hound", "Please select a microphone."));
                return;
            }

            Mixer.Info selectedMixerInfo = getSelectedMixerInfo(selectedMicrophone);
            if (selectedMixerInfo == null) {
                addMessage(new Message("Sound Hound", "Selected microphone not found."));
                return;
            }

            Mixer mixer = AudioSystem.getMixer(selectedMixerInfo);
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) mixer.getLine(dataLineInfo);
            microphone.open(format);
            microphone.start();

            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (recording) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        synchronized (out) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            recordingThread.start();
        } catch (LineUnavailableException e) {
            addMessage(new Message("Sound Hound", "Error: " + e.getMessage()));
            e.printStackTrace();
        }
    }


    private void stopRecording() {
        recording = false;
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            // Ensure all data is written to the output stream
            byte[] audioData;
            synchronized (out) {
                audioData = out.toByteArray();
            }
            if (audioData.length > 0) {
                // Save the recorded audio to a file
                File audioFile = new File("recorded_audio.wav");
                AudioSystem.write(new AudioInputStream(
                                new ByteArrayInputStream(audioData), format, audioData.length / format.getFrameSize()),
                        AudioFileFormat.Type.WAVE, audioFile);

                // Log the size of the audio file
                System.out.println("Audio file size: " + audioFile.length() + " bytes");

                // Send the recorded audio file to Sound Hound
                sendAudioRequest(audioFile);
            } else {
                addMessage(new Message("Sound Hound", "No audio data captured."));
            }

        } catch (IOException e) {
            addMessage(new Message("Sound Hound", "Error saving audio file: " + e.getMessage()));
            e.printStackTrace();
        }
    }


    private void sendAudioRequest(File audioFile) {
        try {
            // Create request info
            RequestInfoJSON requestInfo = new RequestInfoJSON();
            requestInfo.setUnitPreference(RequestInfoJSON.TypeUnitPreference.UnitPreference_US);
            requestInfo.setRequestID(UUID.randomUUID().toString());
            requestInfo.setSessionID(UUID.randomUUID().toString());
            RequestInfoJSON.TypeClientVersion clientVersion = new RequestInfoJSON.TypeClientVersion();
            clientVersion.key = 0;
            clientVersion.choice0 = "1.0";
            requestInfo.setClientVersion(clientVersion);

            // Read the audio file into a byte array
            InputStream audioStream = new BufferedInputStream(new FileInputStream(audioFile));
            HoundRequester.VoiceRequest request = requester.start_voice_request(
                    isConversationContextActive.isSelected() ? SessionManager.getInstance().getConversationState() : null,
                    requestInfo, new HoundRequester.PartialHandler() {
                        @Override
                        public void handle(HoundPartialTranscriptJSON partial) {
                            // Handle partial transcript if needed
                        }
                    });

            byte[] buffer = new byte[24];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                request.add_audio(bytesRead, buffer);
            }
            audioStream.close();
            HoundServerJSON houndResult = request.finish();

            // Log the full result for debugging
            System.out.println("Hound Audio Result: " + houndResult);

            // Check the result
            if (houndResult != null && houndResult.hasAllResults()) {
                if (houndResult.countOfAllResults() > 0) {
                    String response = houndResult.elementOfAllResults(0).getWrittenResponseLong();
                    addMessage(new Message("Sound Hound", response));

                    // Save the conversation state
                    if (isConversationContextActive.isSelected()) {
                        SessionManager.getInstance().setConversationState(houndResult.elementOfAllResults(0).getConversationState());
                    }
                } else {
                    addMessage(new Message("Sound Hound", "No match."));
                }
            } else if (houndResult != null && houndResult.hasErrorMessage()) {
                addMessage(new Message("Sound Hound", "Error: " + houndResult.getErrorMessage()));
            } else {
                addMessage(new Message("Sound Hound", "No result or error from server."));
            }
        } catch (Exception e) {
            addMessage(new Message("Sound Hound", "Error: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private Mixer.Info getSelectedMixerInfo(String selectedMicrophone) {
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixers) {
            if (mixerInfo.getName().equals(selectedMicrophone)) {
                return mixerInfo;
            }
        }
        return null;
    }

    // Define MessageListCell class
    private class MessageListCell extends ListCell<Message> {
        @Override
        protected void updateItem(Message message, boolean empty) {
            super.updateItem(message, empty);

            if (empty || message == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox hbox = new HBox(5);
                Label senderLabel = new Label(message.getSender() + ": ");
                Label contentLabel = new Label(message.getContent());
                Button listenButton = new Button();
                try {
                    ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream("/com/example/soundhounddemo/images/speaker_icon.png")));
                    imageView.setFitHeight(16);
                    imageView.setFitWidth(16);
                    listenButton.setGraphic(imageView);
                } catch (NullPointerException e) {
                    System.err.println("Error loading image: " + e.getMessage());
                }

                hbox.getChildren().addAll(senderLabel, contentLabel, listenButton);
                setGraphic(hbox);
            }
        }
    }

    public static class Message {
        private final String sender;
        private final String content;

        public Message(String sender, String content) {
            this.sender = sender;
            this.content = content;
        }

        public String getSender() {
            return sender;
        }

        public String getContent() {
            return content;
        }
    }
}
