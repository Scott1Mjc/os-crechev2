package br.com.creche.controller;

import br.com.creche.infra.DB;
import br.com.creche.model.OrdemServico;
import br.com.creche.model.Usuario;
import br.com.creche.repository.OrdemServicoRepository;
import br.com.creche.service.AuthService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import br.com.creche.ui.ThemeManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class EditarOSController {

    @FXML
    private TextField txtNumero, txtTitulo, txtSolicitante;
    @FXML
    private TextArea txtDescricao, txtObservacoes;
    @FXML
    private ComboBox<String> cbCategoria, cbPrioridade, cbStatus;
    @FXML
    private ComboBox<UsuarioItem> cbAtribuidoPara;
    @FXML
    private DatePicker dpPrazo; // apenas DatePicker
    @FXML
    private Label lblErro;

    private final OrdemServicoRepository repo = new OrdemServicoRepository();
    private AuthService authService;
    private OrdemServico os;
    private Runnable onSaved;

    private final ZoneId zoneId = ZoneId.systemDefault();

    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void setOrdemServico(OrdemServico os) {
        this.os = os;
        preencherCampos();
        System.out.println(os.getAtribuidoPara());
        Long responsavelId = Optional.ofNullable(os)
                .map(OrdemServico::getAtribuidoPara)
                .map(Usuario::getId)
                .orElse(null);
        selecionarUsuarioAtribuido(responsavelId);    }

    @FXML
    public void initialize() {
        cbPrioridade.getItems().setAll(Arrays.asList("BAIXA","MEDIA","ALTA"));
        cbStatus.getItems().setAll(Arrays.asList("ABERTA","EM_ANDAMENTO","CONCLUIDA","CANCELADA"));
        carregarUsuarios();
        cbCategoria.setItems(FXCollections.observableArrayList(
                "Infraestrutura", "Limpeza", "Manutenção", "TI", "Administrativo"
        ));
    }

    private void carregarUsuarios() {
        ObservableList<UsuarioItem> usuarios = FXCollections.observableArrayList();
        String sql = "select id, nome, email from usuarios where ativo = true order by nome asc";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                usuarios.add(new UsuarioItem(
                        rs.getLong("id"),
                        rs.getString("nome"),
                        rs.getString("email")
                ));
            }
            cbAtribuidoPara.setItems(usuarios);

            if (os != null && os.getAtribuidoPara() != null) {
                selecionarUsuarioAtribuido(os.getAtribuidoPara().getId());
            }
        } catch (SQLException ex) {
            mostrarErro("Erro ao carregar usuários", ex);
        }
    }

    private void selecionarUsuarioAtribuido(Long usuarioId) {
        if (usuarioId == null || cbAtribuidoPara.getItems() == null) return;
        cbAtribuidoPara.getItems().stream()
                .filter(item -> Objects.equals(item.id(), usuarioId))
                .findFirst()
                .ifPresent(item -> cbAtribuidoPara.getSelectionModel().select(item));
    }

    private void preencherCampos() {
        if (os == null) return;
        txtNumero.setText(os.getNumero());
        txtTitulo.setText(os.getTitulo());
        txtDescricao.setText(os.getDescricao());
        cbCategoria.setValue(os.getCategoria());
        cbPrioridade.setValue(os.getPrioridade());
        cbStatus.setValue(os.getStatus());
        txtSolicitante.setText(os.getSolicitante());
        if (os.getPrazo() != null) {
            dpPrazo.setValue(os.getPrazo().toLocalDate());
        }
        txtObservacoes.setText(os.getObservacoes());
    }

    @FXML
    public void onSalvar() {
        try {
            validar();

            os.setNumero(trim(txtNumero.getText()));
            os.setTitulo(trim(txtTitulo.getText()));
            os.setDescricao(trim(txtDescricao.getText()));
            os.setCategoria(trim(cbCategoria.getValue()));
            os.setPrioridade(cbPrioridade.getValue());
            os.setStatus(cbStatus.getValue());
            os.setSolicitante(trim(txtSolicitante.getText()));
            os.setObservacoes(trim(txtObservacoes.getText()));

            UsuarioItem atribuido = cbAtribuidoPara.getValue();
            if (atribuido != null) {
                Usuario usuario = new Usuario();
                usuario.setId(atribuido.id());
                usuario.setNome(atribuido.nome());
                usuario.setEmail(atribuido.email());
                os.setAtribuidoPara(usuario);
            } else {
                os.setAtribuidoPara(null);
            }

            // Prazo apenas com data: usar meio-dia local para evitar problemas de DST
            OffsetDateTime prazo = null;
            if (dpPrazo != null && dpPrazo.getValue() != null) {
                var localDateTime = dpPrazo.getValue().atTime(12, 0);
                var offset = zoneId.getRules().getOffset(Instant.now());
                prazo = OffsetDateTime.of(localDateTime, offset);
            }
            os.setPrazo(prazo);

            if (os.getId() == null) {
                Long usuarioLogadoId = authService != null && authService.getUsuarioLogado() != null
                        ? authService.getUsuarioLogado().getId() : null;
                repo.insert(os, usuarioLogadoId);
            } else {
                repo.update(os);
            }

            if (onSaved != null) onSaved.run();
            fechar();

        } catch (Exception e) {
            lblErro.setText(Optional.ofNullable(e.getMessage()).orElse(e.toString()));
            lblErro.setVisible(true);
        }
    }

    private void validar() {
        StringBuilder erros = new StringBuilder();
        if (isBlank(txtNumero.getText())) erros.append("- Número é obrigatório.\n");
        if (!isBlank(txtNumero.getText()) && txtNumero.getText().length() > 30) erros.append("- Número excede 30 caracteres.\n");
        if (isBlank(txtTitulo.getText())) erros.append("- Título é obrigatório.\n");
        if (!isBlank(txtTitulo.getText()) && txtTitulo.getText().length() > 200) erros.append("- Título excede 200 caracteres.\n");
        if (cbPrioridade.getValue() == null) erros.append("- Selecione a prioridade.\n");
        if (cbStatus.getValue() == null) erros.append("- Selecione o status.\n");

        if (erros.length() > 0) {
            throw new IllegalArgumentException(erros.toString());
        }
    }

    @FXML
    public void onCancelar() {
        fechar();
    }

    private void fechar() {
        Stage stage = (Stage) txtTitulo.getScene().getWindow();
        stage.close();
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private void mostrarErro(String title, Exception ex) {
        String msg = Optional.ofNullable(ex.getMessage()).orElse(ex.toString());
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.showAndWait();
        ex.printStackTrace();
    }

    public static class UsuarioItem {
        private final Long id;
        private final String nome;
        private final String email;
        public UsuarioItem(Long id, String nome, String email) { this.id = id; this.nome = nome; this.email = email; }
        public Long id() { return id; }
        public String nome() { return nome; }
        public String email() { return email; }
        @Override public String toString() { return nome + (email != null ? " (" + email + ")" : ""); }
    }
}