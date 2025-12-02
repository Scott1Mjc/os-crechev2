package br.com.creche.controller;

import br.com.creche.infra.DB;
import br.com.creche.model.OrdemServico;
import br.com.creche.model.Perfil;
import br.com.creche.model.Usuario;
import br.com.creche.repository.OrdemServicoRepository;
import br.com.creche.service.AuthService;
import br.com.creche.ui.SceneFactory;
import br.com.creche.ui.ThemeManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class DashboardController {

    private AuthService authService;
    private javafx.scene.Node dashboardCenterOriginal;

    public void setAuthService(AuthService authService) {
        this.authService = authService;
        configurarPermissoes(); // aplica visibilidade/enable de botões se quiser
    }

    @FXML
    private BorderPane root;
    @FXML
    private TextField txtBuscaGlobal;
    @FXML
    private MenuButton mbUser;
    @FXML
    private ToggleButton tbDashboard, tbOS, tbUsuarios, tbRelatorios, tbConfiguracoes;
    @FXML
    private Label lblKpiAbertas, lblKpiEmAndamento, lblKpiConcluidas, lblKpiAtrasadas, telaAtual;
    @FXML
    private TableView<OrdemServico> tvOSRecentes;
    @FXML
    private TableColumn<OrdemServico, String> colNumero, colTitulo, colCategoria, colResponsavel;
    @FXML
    private TableColumn<OrdemServico, String> colStatus, colPrazo, colPrioridade, colSolicitante;
    @FXML
    private ChoiceBox<String> cbFiltroStatus;
    @FXML
    private Button btnNovaOS;
    @FXML
    private PieChart pcStatus;



    private final OrdemServicoRepository repo = new OrdemServicoRepository();
    private final DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy"); // prazo só data

    // Cache opcional para views carregadas
    private final Map<String, Parent> cacheViews = new HashMap<>();

    @FXML
    public void initialize() {

        // Filtro de status
        cbFiltroStatus.setItems(FXCollections.observableArrayList("Todos", "ABERTA", "EM_ANDAMENTO", "CONCLUIDA", "CANCELADA"));
        cbFiltroStatus.getSelectionModel().selectFirst();
        cbFiltroStatus.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> carregarDados());

        // Tabela
        colNumero.setCellValueFactory(new PropertyValueFactory<>("numero"));
        colTitulo.setCellValueFactory(new PropertyValueFactory<>("titulo"));
        colCategoria.setCellValueFactory(new PropertyValueFactory<>("categoria"));
        colPrioridade.setCellValueFactory(new PropertyValueFactory<>("prioridade"));
        colSolicitante.setCellValueFactory(new PropertyValueFactory<>("solicitante"));

        colResponsavel.setCellValueFactory(cd -> {
            Usuario responsavel = cd.getValue().getAtribuidoPara();
            String nome = responsavel != null ? responsavel.getNome() : "";
            return new SimpleStringProperty(nome);
        });

        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colPrazo.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getPrazo() != null ? df.format(cd.getValue().getPrazo()) : ""
        ));

        // Duplo clique abre editor
        tvOSRecentes.setRowFactory(tv -> {
            TableRow<OrdemServico> row = new TableRow<>();
            row.setOnMouseClicked(evt -> {
                if (evt.getClickCount() == 2 && !row.isEmpty()) {
                    OrdemServico os = row.getItem();
                    abrirEditor(os);
                }
            });
            return row;
        });

        // Carregar dados iniciais
        carregarDados();

        // Seleciona menu inicial visualmente
        selecionarMenu(tbDashboard);
        dashboardCenterOriginal = root.getCenter();
    }

    private void carregarDados() {
        List<OrdemServico> list = repo.findRecentes(100);

        // Filtro
        String filtro = cbFiltroStatus.getValue();
        if (filtro != null && !"Todos".equalsIgnoreCase(filtro)) {
            list = list.stream()
                    .filter(o -> filtro.equalsIgnoreCase(o.getStatus()))
                    .collect(Collectors.toList());
        }

        // Tabela
        tvOSRecentes.setItems(FXCollections.observableArrayList(list));

        // KPIs (calcula com base no universo filtrado exibido)
        long abertas = list.stream().filter(o -> "ABERTA".equals(o.getStatus())).count();
        long andamento = list.stream().filter(o -> "EM_ANDAMENTO".equals(o.getStatus())).count();
        long concluidas = list.stream().filter(o -> "CONCLUIDA".equals(o.getStatus())).count();
        OffsetDateTime agora = OffsetDateTime.now();
        long atrasadas = list.stream()
                .filter(o -> o.getPrazo() != null && o.getPrazo().isBefore(agora) && !"CONCLUIDA".equals(o.getStatus()))
                .count();

        lblKpiAbertas.setText(String.valueOf(abertas));
        lblKpiEmAndamento.setText(String.valueOf(andamento));
        lblKpiConcluidas.setText(String.valueOf(concluidas));
        lblKpiAtrasadas.setText(String.valueOf(atrasadas));

        // PieChart por status
        Map<String, Long> porStatus = list.stream()
                .collect(Collectors.groupingBy(OrdemServico::getStatus, Collectors.counting()));

        pcStatus.setData(FXCollections.observableArrayList(
                porStatus.entrySet().stream()
                        .map(e -> new PieChart.Data(e.getKey(), e.getValue()))
                        .collect(Collectors.toList())
        ));
    }

    private void abrirEditor(OrdemServico os) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editar-os.fxml"));
            Parent rootNode = loader.load();

            EditarOSController controller = loader.getController();
            controller.setAuthService(this.authService);
            controller.setOrdemServico(os);
            controller.setOnSaved(this::carregarDados);

            Scene scene = SceneFactory.createScene(rootNode);

            Stage stage = new Stage();
            stage.setTitle("Editar O.S. - " + os.getNumero());
            stage.setScene(scene);
            stage.initOwner(tvOSRecentes.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir editor: " + e.getMessage()).showAndWait();
        }
    }

    private void selecionarMenu(ToggleButton selected) {
        tbDashboard.setSelected(false);
        tbOS.setSelected(false);
        tbUsuarios.setSelected(false);
        tbRelatorios.setSelected(false);
        tbConfiguracoes.setSelected(false);
        if (selected != null) selected.setSelected(true);
    }

    // Navegação – use setCenterCached para performance
    @FXML
    public void goDashboard() {
        selecionarMenu(tbDashboard);
        root.setCenter(dashboardCenterOriginal);
        telaAtual.setText("Dashboard");
    }

    @FXML
    public void goOS() {
        selecionarMenu(tbOS);
        setCenterCached("/fxml/os-lista.fxml");
        telaAtual.setText("Editar/Incluir Ordem de Serviço");
    }

    @FXML
    public void goUsuarios() {
        selecionarMenu(tbUsuarios);
        setCenterCached("/fxml/usuarios.fxml");
        telaAtual.setText("Editar/Incluir Usuário");
    }

    @FXML
    public void goRelatorios() {
        selecionarMenu(tbRelatorios);
        setCenterCached("/fxml/relatorios.fxml");
        telaAtual.setText("Relatórios");
    }

    @FXML
    public void goConfiguracoes() {
        selecionarMenu(tbConfiguracoes);
        setCenterCached("/fxml/configuracoes.fxml");
        telaAtual.setText("Configurações");
    }

    private void setCenterCached(String fxmlPath) {
        try {
            Parent content = cacheViews.get(fxmlPath);
            if (content == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                content = loader.load();

                Object controller = loader.getController();
                if (controller instanceof RequiresAuthService c && this.authService != null) {
                    c.setAuthService(this.authService);
                }

                cacheViews.put(fxmlPath, content);
            }
            root.setCenter(content);
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Falha ao carregar: " + fxmlPath + "\n" + e.getMessage()).showAndWait();
        }
    }

    public interface RequiresAuthService {
        void setAuthService(AuthService authService);
    }

    @FXML
    public void onNovaOS() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/nova-os.fxml"));
            Parent rootNode = loader.load();

            NovaOSController controller = loader.getController();
            controller.setAuthService(this.authService);
            controller.setOnSaved(this::carregarDados);

            Scene scene = SceneFactory.createScene(rootNode);

            Stage stage = new Stage();
            stage.setTitle("Nova Ordem de Serviço");
            stage.setScene(scene);
            stage.initOwner(btnNovaOS.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Nova O.S.: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    public void onApagarOS() {
        OrdemServico selecionada = tvOSRecentes.getSelectionModel().getSelectedItem();
        if (selecionada == null) {
            showWarning("Nenhuma seleção", "Selecione uma O.S. na tabela para apagar.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Exclusão");
        confirm.setHeaderText("Deseja realmente apagar esta O.S.?");
        confirm.setContentText("Número: " + selecionada.getNumero());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            repo.delete(selecionada.getId());
            showInfo("O.S. Removida", "A O.S. número " + selecionada.getNumero() + " foi apagada com sucesso.");
            carregarDados();
        } catch (Exception ex) {
            showError("Erro ao apagar O.S.", ex);
        }
    }

    @FXML
    public void onPerfil() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        alert.setTitle("Perfil de Usuário");
        alert.setHeaderText("Funcionalidade em desenvolvimento");
        alert.setContentText("A aba de perfil será implementada em breve. Agradecemos a sua paciência.");

        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        alert.showAndWait();
    }


    @FXML
        public void onSair() {
        Stage stage = (Stage) pcStatus.getScene().getWindow();
        stage.close();
    }

    @FXML
    public void onSairESemLembrar() {
        Preferences prefs = Preferences.userRoot().node("br.com.creche.login");
        prefs.remove("email");
        prefs.remove("senha");

        Stage stage = (Stage) pcStatus.getScene().getWindow();
        stage.close();
    }

    private void configurarPermissoes() {
        if (authService == null || authService.getUsuarioLogado() == null) return;

        Usuario user = authService.getUsuarioLogado();
        Perfil perfil = user.getPerfilEnum();

        boolean isAdmin = perfil == Perfil.ADMIN;
        boolean isGestor = perfil == Perfil.GESTOR;
        boolean isOperador = perfil == Perfil.OPERADOR;

        // tbUsuarios.setVisible(isAdmin || isGestor);
        // tbUsuarios.setManaged(isAdmin || isGestor);
        //
        // tbRelatorios.setVisible(isAdmin || isGestor);
        // tbRelatorios.setManaged(isAdmin || isGestor);
        //
        // tbConfiguracoes.setVisible(isAdmin);
        // tbConfiguracoes.setManaged(isAdmin);

        if (btnNovaOS != null) {
            btnNovaOS.setDisable(!(isAdmin || isGestor || isOperador));
        }
    }

    private void showError(String title, Exception ex) {
        String msg = Optional.ofNullable(ex.getMessage()).orElse(ex.toString());
        showAlert(Alert.AlertType.ERROR, title, msg);
        ex.printStackTrace();
    }

    private void showInfo(String title, String content) {
        showAlert(Alert.AlertType.INFORMATION, title, content);
    }

    private void showWarning(String title, String content) {
        showAlert(Alert.AlertType.WARNING, title, content);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.showAndWait();
    }
}
