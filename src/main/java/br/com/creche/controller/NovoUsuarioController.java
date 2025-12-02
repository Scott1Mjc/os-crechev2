package br.com.creche.controller;

import br.com.creche.model.Usuario;
import br.com.creche.ui.ThemeManager;
import br.com.creche.repository.UsuarioRepository;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;

public class NovoUsuarioController {

    @FXML
    private TextField txtNome, txtEmail, txtSenhaVisivel;
    @FXML
    private PasswordField txtSenha;
    @FXML
    private ComboBox<String> cbPerfil;
    @FXML
    private CheckBox chkAtivo;
    @FXML
    private Label lblErro;
    @FXML
    private ImageView btnMostrarSenha, btnOcultarSenha;

    private boolean senhaVisivel = false;
    private final UsuarioRepository repository = new UsuarioRepository();
    private Runnable onSaved;

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    @FXML
    public void initialize() {
        txtSenhaVisivel.setVisible(false);
        btnOcultarSenha.setVisible(false);
        // Carregar perfis disponíveis
        cbPerfil.getItems().addAll("ADMIN", "GESTOR", "OPERADOR");
        cbPerfil.getSelectionModel().selectFirst();
    }

    // Configura o botão de visualizar senha
    @FXML
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
    public void onSalvar() {
        try {
            validar();

            Usuario usuario = new Usuario();
            usuario.setNome(txtNome.getText().trim());
            usuario.setEmail(txtEmail.getText().trim().toLowerCase());
            usuario.setPerfil(cbPerfil.getValue());
            usuario.setAtivo(chkAtivo.isSelected());
            
            // Gerar hash da senha usando BCrypt
            String senhaHash = BCrypt.hashpw(txtSenha.getText(), BCrypt.gensalt());
            usuario.setSenhaHash(senhaHash);

            repository.insert(usuario);

            if (onSaved != null) onSaved.run();
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Sucesso");
            alert.setHeaderText(null);
            alert.setContentText("Usuário criado com sucesso!");
            alert.showAndWait();
            
            fechar();

        } catch (Exception e) {
            lblErro.setText(e.getMessage());
            lblErro.setVisible(true);
            lblErro.setManaged(true);
        }
    }

    private void validar() {
        StringBuilder erros = new StringBuilder();

        if (isBlank(txtNome.getText())) {
            erros.append("- Nome é obrigatório.\n");
        }

        if (isBlank(txtEmail.getText())) {
            erros.append("- E-mail é obrigatório.\n");
        } else if (!isEmailValido(txtEmail.getText())) {
            erros.append("- E-mail inválido.\n");
        }

        if (isBlank(txtSenha.getText())) {
            erros.append("- Senha é obrigatória.\n");
        } else if (txtSenha.getText().length() < 6) {
            erros.append("- Senha deve ter no mínimo 6 caracteres.\n");
        }

        if (cbPerfil.getValue() == null) {
            erros.append("- Selecione o perfil.\n");
        }

        if (erros.length() > 0) {
            throw new IllegalArgumentException(erros.toString());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isEmailValido(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    @FXML
    public void onCancelar() {
        fechar();
    }

    private void fechar() {
        Stage stage = (Stage) txtNome.getScene().getWindow();
        stage.close();
    }
}