module com.example.soundhounddemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires houndify.java.sdk;
    requires javafx.media;
    requires java.desktop;

    opens com.example.soundhounddemo to javafx.fxml;
    opens com.example.soundhounddemo.fxml.controllers to javafx.fxml;

    exports com.example.soundhounddemo;
}
