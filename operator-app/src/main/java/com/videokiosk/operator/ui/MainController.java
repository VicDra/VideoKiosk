package com.videokiosk.operator.ui;

import com.videokiosk.operator.model.KioskCall;
import com.videokiosk.operator.viewmodel.MainViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

/**
 * JavaFX controller for main-view.fxml.
 * Handles the list of incoming calls and accept/reject actions.
 */
public class MainController {

    // ---------------------------------------------------------------------------
    // FXML bindings
    // ---------------------------------------------------------------------------

    @FXML
    private ListView<KioskCall> callsListView;

    @FXML
    private Button acceptButton;

    @FXML
    private Button rejectButton;

    @FXML
    private Label statusLabel;

    // ---------------------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------------------

    private final MainViewModel viewModel = new MainViewModel();

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    @FXML
    public void initialize() {
        // Bind ListView items to ViewModel's observable list
        callsListView.setItems(viewModel.getIncomingCalls());

        // Disable buttons when no item is selected
        acceptButton.disableProperty().bind(
                callsListView.getSelectionModel().selectedItemProperty().isNull()
                        .or(viewModel.inCallProperty()));
        rejectButton.disableProperty().bind(
                callsListView.getSelectionModel().selectedItemProperty().isNull());

        // Update status label when inCall changes
        viewModel.inCallProperty().addListener((obs, wasInCall, nowInCall) -> {
            if (nowInCall) {
                statusLabel.setText("В разговоре с: " +
                        viewModel.getActiveCall().getClientId());
            } else {
                statusLabel.setText("Ожидание вызовов...");
            }
        });

        statusLabel.setText("Ожидание вызовов...");

        // TODO: obtain SignalingService from application context and wire it to ViewModel
    }

    // ---------------------------------------------------------------------------
    // Button handlers
    // ---------------------------------------------------------------------------

    @FXML
    public void handleAccept() {
        KioskCall selected = callsListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        viewModel.acceptCall(selected);

        // TODO: open call-view.fxml in a new window or replace current scene
        System.out.println("[MainController] Accepted call from: " + selected.getClientId());
    }

    @FXML
    public void handleReject() {
        KioskCall selected = callsListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        viewModel.rejectCall(selected);
        System.out.println("[MainController] Rejected call from: " + selected.getClientId());
    }
}
