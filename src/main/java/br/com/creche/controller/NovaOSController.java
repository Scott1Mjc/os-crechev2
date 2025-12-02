package br.com.creche.controller;

import br.com.creche.infra.DB;
import br.com.creche.model.Usuario;
import br.com.creche.service.AuthService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import br.com.creche.ui.ThemeManager;

import java.sql.*;
import java.time.*;
import java.util.Objects;
import java.util.Optional;

public class NovaOSController {

    @FXML
    private TextField txtNumero, txtTitulo, txtSolicitante;
    @FXML
    private ComboBox<String> cbCategoria, cbPrioridade, cbStatus;
    @FXML
    private ComboBox<UsuarioItem> cbAtribuidoPara;
    @FXML
    private DatePicker dpPrazo;
    @FXML
    private TextArea txtObservacoes, txtDescricao;
    @FXML
    private Button btnSalvar, btnSalvarRodape, btnCancelar, btnLimpar, btnVoltar;

    private ThemeManager.Theme temaSelecionado = ThemeManager.Theme.SISTEMA;
    private Runnable onSaved;

    // Injetado pelo chamador (ex: Dashboard) após carregar o FXML
    private AuthService authService;

    // Guardamos o ID do usuário logado para usar em criado_por e no histórico
    private Long usuarioLogadoId = null;

    public void setAuthService(AuthService authService) {
        this.authService = authService;
        // Após injetar, definimos o usuário logado
        Usuario u = (authService != null) ? authService.getUsuarioLogado() : null;
        this.usuarioLogadoId = (u != null) ? u.getId() : null;

        // Se a combo de usuários já estiver carregada, podemos pré-selecionar o logado
//        if (u != null && cbAtribuidoPara != null && cbAtribuidoPara.getItems() != null) {
//            cbAtribuidoPara.getItems().stream()
//                    .filter(item -> Objects.equals(item.id(), u.getId()))
//                    .findFirst()
//                    .ifPresent(item -> cbAtribuidoPara.getSelectionModel().select(item));
//        }
    }

    private final ZoneId zoneId = ZoneId.systemDefault();

    @FXML
    public void initialize() {
        cbPrioridade.setItems(FXCollections.observableArrayList("BAIXA", "MEDIA", "ALTA"));
        cbPrioridade.getSelectionModel().select("MEDIA");

        cbStatus.setItems(FXCollections.observableArrayList("ABERTA", "EM_ANDAMENTO", "CONCLUIDA", "CANCELADA"));
        cbStatus.getSelectionModel().select("ABERTA");

        cbCategoria.setItems(FXCollections.observableArrayList(
                "Infraestrutura", "Limpeza", "Manutenção", "TI", "Administrativo"
        ));

        carregarUsuarios();

        if (isBlank(txtNumero.getText())) {
            txtNumero.setText(gerarNumeroOS());
        }

        // Enter para salvar em campos chave
        txtTitulo.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) onSalvar();
        });
        txtNumero.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) onSalvar();
        });
        txtSolicitante.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) onSalvar();
        });
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
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
        } catch (SQLException ex) {
            showError("Erro ao carregar usuários", ex);
        }
    }

    private String gerarNumeroOS() {
        LocalDateTime now = LocalDateTime.now();
        String base = String.format("OS-%1$tY%1$tm%1$td-%1$tH%1$tM%1$tS", now);
        int rand = (int) (Math.random() * 90 + 10);
        return base + "-" + rand;
    }

    @FXML
    public void onSalvar() {
        String numero = trimOrEmpty(txtNumero.getText());
        String titulo = trimOrEmpty(txtTitulo.getText());
        String categoria = cbCategoria.getValue();
        String solicitante = trimOrEmpty(txtSolicitante.getText());
        String descricao = trimOrEmpty(txtDescricao.getText());
        String prioridade = cbPrioridade.getValue();
        String status = cbStatus.getValue();
        UsuarioItem atribuido = cbAtribuidoPara.getValue();
        LocalDate prazoDate = dpPrazo.getValue();
        String observacoes = trimOrEmpty(txtObservacoes.getText());

        if (usuarioLogadoId == null) {
            showWarning("Sessão", "Nenhum usuário logado. Faça login novamente.");
            return;
        }

        StringBuilder erros = new StringBuilder();
        if (isBlank(numero)) erros.append("- Número é obrigatório.\n");
        if (numero.length() > 30) erros.append("- Número excede 30 caracteres.\n");
        if (isBlank(titulo)) erros.append("- Título é obrigatório.\n");
        if (titulo.length() > 200) erros.append("- Título excede 200 caracteres.\n");
        if (categoria != null && categoria.length() > 60) erros.append("- Categoria excede 60 caracteres.\n");
        if (solicitante.length() > 120) erros.append("- Solicitante excede 120 caracteres.\n");

        if (erros.length() > 0) {
            showWarning("Validação", erros.toString());
            return;
        }

        // Converte LocalDate para Timestamp com timezone coerente (meio-dia local para evitar problemas de DST)
        Timestamp prazoTs = null;
        if (prazoDate != null) {
            LocalDateTime localNoon = prazoDate.atTime(12, 0);
            Instant instant = localNoon.atZone(zoneId).toInstant();
            prazoTs = Timestamp.from(instant);
        }

        Long atribuidoId = (atribuido != null ? atribuido.id() : null);

        String insertOS = """
                insert into ordens_servico
                (numero, titulo, descricao, categoria, prioridade, status, solicitante, prazo, criado_por, atribuido_para, observacoes)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """;

        String insertHist = """
                insert into os_historico (os_id, acao, de_status, para_status, usuario_id, observacao)
                values (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            Long osId;

            Usuario logado = authService.getUsuarioLogado();
            if (logado == null) {
                showError("Nenhum usuário está logado.", new Exception("Usuário não autenticado."));
                return;
            }
            Long usuarioLogadoId = logado.getId();

            int attempts = 0;
            while (true) {
                try (PreparedStatement ps = conn.prepareStatement(insertOS)) {
                    ps.setString(1, numero);
                    ps.setString(2, titulo);
                    ps.setString(3, emptyToNull(descricao));
                    ps.setString(4, emptyToNull(categoria));
                    ps.setString(5, prioridade);
                    ps.setString(6, status);
                    ps.setString(7, emptyToNull(solicitante));
                    if (prazoTs != null) ps.setTimestamp(8, prazoTs);
                    else ps.setNull(8, Types.TIMESTAMP);
                    if (usuarioLogadoId != null) ps.setLong(9, usuarioLogadoId);
                    else ps.setNull(9, Types.BIGINT);
                    if (atribuidoId != null) ps.setLong(10, atribuidoId);
                    else ps.setNull(10, Types.BIGINT);
                    ps.setString(11, emptyToNull(observacoes));

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        osId = rs.getLong(1);
                    }
                    break;
                } catch (SQLException ex) {
                    if (isUniqueViolation(ex) && messageContains(ex, "ordens_servico_numero_key")) {
                        if (++attempts > 3) throw ex;
                        numero = gerarNumeroOS();
                        txtNumero.setText(numero);
                    } else {
                        throw ex;
                    }
                }
            }

            try (PreparedStatement hs = conn.prepareStatement(insertHist)) {
                hs.setLong(1, osId);
                hs.setString(2, "CRIACAO");
                hs.setString(3, null);
                hs.setString(4, status);
                if (usuarioLogadoId != null) hs.setLong(5, usuarioLogadoId);
                else hs.setNull(5, Types.BIGINT);
                hs.setString(6, emptyToNull("O.S. criada" + (isBlank(observacoes) ? "" : " - " + observacoes)));
                hs.executeUpdate();
            }

            conn.commit();

            showInfo("Sucesso", "Ordem de Serviço criada com sucesso!\nNúmero: " + numero);

            if (onSaved != null) {
                try { onSaved.run(); } catch (Exception ignore) {}
            }

            limparCampos(false);

            // Se quiser fechar a janela após salvar:
            fecharJanela();
        } catch (SQLException ex) {
            showError("Erro ao salvar O.S.", ex);
        }
    }

    @FXML
    public void onCancelar() {
        boolean sair = confirm("Cancelar criação?", "As alterações serão perdidas.");
        if (sair) fecharJanela();
    }

    @FXML
    public void onLimpar() {
        boolean ok = confirm("Limpar campos?", "Todos os campos serão redefinidos.");
        if (ok) limparCampos(true);
    }

    @FXML
    public void onVoltar() {
        fecharJanela();
    }

    private void limparCampos(boolean gerarNovoNumero) {
        if (gerarNovoNumero) txtNumero.setText(gerarNumeroOS());
        cbCategoria.getSelectionModel().clearSelection();
        txtTitulo.clear();
        txtSolicitante.clear();
        txtDescricao.clear();
        cbPrioridade.getSelectionModel().select("MEDIA");
        cbStatus.getSelectionModel().select("ABERTA");
        cbAtribuidoPara.getSelectionModel().clearSelection();
        dpPrazo.setValue(null);
        txtObservacoes.clear();
    }

    private void fecharJanela() {
        Button any = btnVoltar != null ? btnVoltar : (btnCancelar != null ? btnCancelar : btnSalvar);
        if (any != null) {
            any.getScene().getWindow().hide();
        }
    }

    // Utils
    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String emptyToNull(String s) {
        return isBlank(s) ? null : s;
    }

    private static boolean isUniqueViolation(SQLException ex) {
        return Objects.equals(ex.getSQLState(), "23505");
    }

    private static boolean messageContains(SQLException ex, String needle) {
        String m = ex.getMessage();
        return m != null && m.contains(needle);
    }

    private void showInfo(String title, String content) {
        showAlert(Alert.AlertType.INFORMATION, title, content);
    }

    private void showWarning(String title, String content) {
        showAlert(Alert.AlertType.WARNING, title, content);
    }

    private void showError(String title, Exception ex) {
        String msg = Optional.ofNullable(ex.getMessage()).orElse(ex.toString());
        showAlert(Alert.AlertType.ERROR, title, msg);
        ex.printStackTrace();
    }

    private boolean confirm(String title, String content) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        return a.showAndWait().filter(bt -> bt == ButtonType.OK).isPresent();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.showAndWait();
    }

    public static class UsuarioItem {
        private final Long id;
        private final String nome;
        private final String email;

        public UsuarioItem(Long id, String nome, String email) {
            this.id = id;
            this.nome = nome;
            this.email = email;
        }

        public Long id() {
            return id;
        }

        public String nome() {
            return nome;
        }

        public String email() {
            return email;
        }

        @Override
        public String toString() {
            return nome + (email != null ? " (" + email + ")" : "");
        }
    }
}