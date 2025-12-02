package br.com.creche.controller;

import br.com.creche.model.OrdemServico;

import br.com.creche.model.Perfil;
import br.com.creche.model.Usuario;
import br.com.creche.repository.OrdemServicoRepository;
import br.com.creche.service.AuthService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import br.com.creche.ui.ThemeManager;
import br.com.creche.ui.SceneFactory;

import java.net.URL;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class OSListaController implements DashboardController.RequiresAuthService {

    @FXML
    private ChoiceBox<String> cbFiltroStatusLista;
    @FXML
    private TextField txtBuscaOS;
    @FXML
    private Button btnNovaOSLista, btnEditarOSLista;
    @FXML
    private TableView<OrdemServico> tvOS;
    @FXML
    private TableColumn<OrdemServico, String> colNumero, colTitulo, colResponsavel, colSolicitante;
    @FXML
    private TableColumn<OrdemServico, String> colCategoria, colPrioridade, colStatus, colPrazo;

    private final OrdemServicoRepository repo = new OrdemServicoRepository();
    private final DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private AuthService authService;

    @Override
    public void setAuthService(AuthService authService) {
        this.authService = authService;
        configurarPermissoes();
    }

    @FXML
    public void initialize() {
        // status
        cbFiltroStatusLista.setItems(FXCollections.observableArrayList("Todos", "ABERTA", "EM_ANDAMENTO", "CONCLUIDA", "CANCELADA"));
        cbFiltroStatusLista.getSelectionModel().selectFirst();
        cbFiltroStatusLista.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> carregar());

        // tabela
        colNumero.setCellValueFactory(new PropertyValueFactory<>("numero"));
        colTitulo.setCellValueFactory(new PropertyValueFactory<>("titulo"));
        colCategoria.setCellValueFactory(new PropertyValueFactory<>("categoria"));
        colSolicitante.setCellValueFactory(new PropertyValueFactory<>("solicitante"));

        colResponsavel.setCellValueFactory(new PropertyValueFactory<>("atribuidoPara"));

        colPrioridade.setCellValueFactory(new PropertyValueFactory<>("prioridade"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colPrazo.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().getPrazo() != null ? df.format(cd.getValue().getPrazo()) : ""
        ));

        // duplo clique: editar
        tvOS.setRowFactory(tv -> {
            TableRow<OrdemServico> row = new TableRow<>();
            row.setOnMouseClicked(evt -> {
                if (evt.getClickCount() == 2 && !row.isEmpty()) {
                    abrirEditor(row.getItem());
                }
            });
            return row;
        });

        carregar();
    }

    private void configurarPermissoes() {
        if (authService == null || authService.getUsuarioLogado() == null) return;

        Usuario user = authService.getUsuarioLogado();
        Perfil perfil = Perfil.from(user.getPerfil()); // converte a String do BD em enum

        boolean podeCriar =
                perfil == Perfil.ADMIN ||
                        perfil == Perfil.GESTOR ||
                        perfil == Perfil.OPERADOR;

        if (btnNovaOSLista != null) {
            btnNovaOSLista.setDisable(!podeCriar);
        }
    }

    @FXML
    public void onBuscar() {
        carregar();
    }

    private void carregar() {
        // Base: últimas 500 (ajuste conforme necessário)
        List<OrdemServico> list = repo.findRecentes(500);

        // filtro status
        String filtroStatus = cbFiltroStatusLista.getValue();
        if (filtroStatus != null && !"Todos".equalsIgnoreCase(filtroStatus)) {
            list = list.stream()
                    .filter(o -> filtroStatus.equalsIgnoreCase(o.getStatus()))
                    .collect(Collectors.toList());
        }

        // busca texto - apenas por número ou título
        String q = txtBuscaOS.getText();
        if (q != null && !q.trim().isEmpty()) {
            String term = q.trim().toLowerCase(Locale.ROOT);
            list = list.stream().filter(o -> {
                String numero = safe(o.getNumero());
                String titulo = safe(o.getTitulo());
                return numero.contains(term) || titulo.contains(term);
            }).collect(Collectors.toList());
        }

        tvOS.setItems(FXCollections.observableArrayList(list));
    }

    private String safe(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }

    private void abrirEditor(OrdemServico os) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editar-os.fxml"));
            Parent root = loader.load();

            EditarOSController controller = loader.getController();
            controller.setAuthService(this.authService);
            controller.setOrdemServico(os);
            controller.setOnSaved(this::carregar);

            Scene scene = SceneFactory.createScene(root);

            Stage stage = new Stage();
            stage.setTitle("Editar O.S. - " + os.getNumero());
            stage.setScene(scene);
            // se você estiver dentro do center, pegue a janela pelo próprio TableView
            stage.initOwner(tvOS.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir editor: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    public void onNovaOS() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/nova-os.fxml"));
            Parent root = loader.load();

            NovaOSController controller = loader.getController();
            controller.setAuthService(this.authService);

            Scene scene = SceneFactory.createScene(root);

            Stage stage = new Stage();
            stage.setTitle("Nova Ordem de Serviço");
            stage.setScene(scene);
            stage.initOwner(btnNovaOSLista.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir Nova O.S.: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    public void onEditarOS() {
        OrdemServico os = tvOS.getSelectionModel().getSelectedItem();
        if (os != null) {
            abrirEditor(os);
        } else {
            new Alert(Alert.AlertType.WARNING, "Selecione uma Ordem de Serviço na tabela para editar.").showAndWait();
        }
    }
}