package br.com.creche.controller;

import br.com.creche.model.Usuario;
import br.com.creche.repository.UsuarioRepository;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;

public class EditarUsuarioController {

    @FXML private TextField txtNome;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtSenha;
    @FXML private ComboBox<String> cbPerfil;
    @FXML private CheckBox chkAtivo;
    @FXML private Label lblErro;

    private final UsuarioRepository repository = new UsuarioRepository();
    private Usuario usuario;
    private Runnable onSaved;

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        preencherCampos();
    }

    @FXML
    public void initialize() {
        // Carregar perfis disponíveis
        cbPerfil.getItems().addAll("ADMIN", "GESTOR", "OPERADOR");
    }

    private void preencherCampos() {
        if (usuario == null) return;

        txtNome.setText(usuario.getNome());
        txtEmail.setText(usuario.getEmail());
        cbPerfil.setValue(usuario.getPerfil());
        chkAtivo.setSelected(usuario.isAtivo());
        // Senha não é preenchida por segurança
    }

    @FXML
    public void onSalvar() {
        try {
            validar();

            usuario.setNome(txtNome.getText().trim());
            usuario.setEmail(txtEmail.getText().trim().toLowerCase());
            usuario.setPerfil(cbPerfil.getValue());
            usuario.setAtivo(chkAtivo.isSelected());
            
            // Se preencheu a senha, gera novo hash
            if (!isBlank(txtSenha.getText())) {
                String senhaHash = BCrypt.hashpw(txtSenha.getText(), BCrypt.gensalt());
                usuario.setSenhaHash(senhaHash);
            }
            // Se não preencheu, mantém a senha atual (não altera)

            repository.update(usuario);

            if (onSaved != null) onSaved.run();
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Sucesso");
            alert.setHeaderText(null);
            alert.setContentText("Usuário atualizado com sucesso!");
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

        // Senha é opcional na edição, mas se preencheu, valida
        if (!isBlank(txtSenha.getText()) && txtSenha.getText().length() < 6) {
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