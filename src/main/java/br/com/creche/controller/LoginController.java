package br.com.creche.controller;

import br.com.creche.MainApp;
import br.com.creche.service.AuthService;
import br.com.creche.ui.SceneFactory;
import br.com.creche.ui.ThemeManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;

import java.util.prefs.Preferences;

public class LoginController {

    @FXML private TextField txtEmail;
    @FXML private PasswordField txtSenha;
    @FXML private CheckBox chkLembrar;
    @FXML private Button btnEntrar;
    @FXML private Label lblErro;
    @FXML private TextField txtSenhaVisivel;
    @FXML private ImageView btnMostrarSenha;
    @FXML private ImageView btnOcultarSenha;

    private boolean senhaVisivel = false;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        txtSenhaVisivel.setVisible(false);
        btnOcultarSenha.setVisible(false);
        if (!br.com.creche.infra.DBHealth.testConnection()) {
            lblErro.setText("Sem conexão com o banco. Verifique as credenciais.");
            lblErro.setVisible(true);
        }

        // Verifica remember-me e tenta autologin
        Preferences prefs = Preferences.userRoot().node("br.com.creche.login");
        String email = prefs.get("email", null);
        String senhaEnc = prefs.get("senha", null);

        if (email != null && senhaEnc != null) {
            try {
                String senha = br.com.creche.util.CryptoUtils.decrypt(senhaEnc);

                // Aguarda a cena estar pronta para executar o login e abrir dashboard
                btnEntrar.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((obsWin, oldWindow, newWindow) -> {
                            if (newWindow != null) {
                                // Agora sim o botão está na janela
                                btnEntrar.setDisable(true); // opcional, desabilita enquanto tenta logar

                                // Login automático
                                new Thread(() -> {
                                    try {
                                        boolean ok = authService.login(email, senha);
                                        if (ok) {
                                            // Abrir dashboard na thread da UI
                                            javafx.application.Platform.runLater(() -> abrirDashboard());
                                        } else {
                                            javafx.application.Platform.runLater(() -> showError("Autologin falhou."));
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        javafx.application.Platform.runLater(() -> showError("Erro no autologin: " + e.getMessage()));
                                    } finally {
                                        btnEntrar.setDisable(false);
                                    }
                                }).start();
                            }
                        });
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                showError("Erro ao decifrar senha armazenada.");
            }
        }
    }

    @FXML
    // Configura o botão de visualizar senha
    private void toggleMostrarSenha() {
        senhaVisivel = !senhaVisivel;

        if (senhaVisivel) {
            txtSenhaVisivel.setText(txtSenha.getText());
            txtSenhaVisivel.setVisible(true);
            txtSenha.setVisible(false);

            btnMostrarSenha.setImage(
                    new Image(getClass().getResourceAsStream("/images/olho-cruzado.png"))
            );
        } else {
            txtSenha.setText(txtSenhaVisivel.getText());
            txtSenha.setVisible(true);
            txtSenhaVisivel.setVisible(false);

            btnMostrarSenha.setImage(
                    new Image(getClass().getResourceAsStream("/images/olho.png"))
            );
        }
    }


    @FXML
    public void onEntrar(ActionEvent e) {
        String email = txtEmail.getText() != null ? txtEmail.getText().trim() : "";
        String senha = txtSenha.getText() != null ? txtSenha.getText().trim() : "";

        if (email.isBlank() || senha.isBlank()) {
            showError("Preencha e-mail e senha.");
            return;
        }

        try {
            boolean ok = authService.login(email, senha);
            if (ok) {
                Preferences prefs = Preferences.userRoot().node("br.com.creche.login");

                if (chkLembrar.isSelected()) {
                    prefs.put("email", email);
                    try {
                        String senhaCript = br.com.creche.util.CryptoUtils.encrypt(senha);
                        prefs.put("senha", senhaCript);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    prefs.remove("email");
                    prefs.remove("senha");
                }

                abrirDashboard();
            } else {
                showError("Credenciais inválidas.");
            }
        } catch (Exception ex) {
            ex.printStackTrace(); // LOG no console
            showError("Erro ao autenticar: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
        }
    }

    @FXML
    public void onEsqueciSenha(ActionEvent e) {
        new Alert(Alert.AlertType.INFORMATION, "Contate o administrador para redefinir a senha.").showAndWait();
    }

    private void abrirDashboard() {
        try {
            Stage stage = (Stage) btnEntrar.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            Parent root = loader.load();

            DashboardController controller = loader.getController();

            controller.setAuthService(authService);

            Scene scene = SceneFactory.createScene(root);

            stage.setResizable(true);
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Dashboard: " + e.getMessage()).showAndWait();
        }
    }

    private void showError(String msg) {
        lblErro.setText(msg);
        lblErro.setVisible(true);
    }
}