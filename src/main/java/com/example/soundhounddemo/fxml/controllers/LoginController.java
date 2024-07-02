package com.example.soundhounddemo.fxml.controllers;

import com.example.soundhounddemo.SessionManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField clientIdField;

    @FXML
    private PasswordField clientKeyField;

    @FXML
    private TextField usernameField;

    @FXML
    private void handleLogin(ActionEvent event) throws IOException {
        String clientId = clientIdField.getText();
        String clientKey = clientKeyField.getText();
        String username = usernameField.getText();

        // Store credentials in SessionManager
        SessionManager.getInstance().setCredentials(clientId, clientKey, username);

        // Load the home page
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/soundhounddemo/home-page.fxml"));
        Parent root = loader.load();

        // Close the login stage
        Stage loginStage = (Stage) clientIdField.getScene().getWindow();
        loginStage.close();

        // Open the home stage
        Stage homeStage = new Stage();
        homeStage.setTitle("Home Page");
        homeStage.setScene(new Scene(root));
        homeStage.show();
    }

    @FXML
    private void handleCancel() {
        // Clear the fields
        clientIdField.clear();
        clientKeyField.clear();
        usernameField.clear();
    }
}
