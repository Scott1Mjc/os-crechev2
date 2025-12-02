package br.com.creche.controller;

import br.com.creche.model.OrdemServico;
import br.com.creche.model.Usuario;
import br.com.creche.repository.UsuarioRepository;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.net.URL;
import java.util.List;
import br.com.creche.ui.ThemeManager;
import br.com.creche.ui.SceneFactory;

public class UsuarioController {

    @FXML
    private TextField txtBuscaUsuario;
    @FXML
    private Button btnNovoUsuario, btnExcluirUsuario, btnInativarUsuario, btnBuscarUsuario, btnEditarUsuario;
    @FXML
    private TableView<Usuario> tvUsuarios;
    @FXML
    private TableColumn<Usuario, String> colNome, colEmail, colPapeis, colAtivo;

    private final UsuarioRepository usuarioRepository = new UsuarioRepository();

    @FXML
    public void initialize() {
        carregar();

        colNome.setCellValueFactory(new PropertyValueFactory<>("nome"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colPapeis.setCellValueFactory(new PropertyValueFactory<>("perfil"));
        colAtivo.setCellValueFactory(new PropertyValueFactory<>("ativo"));

        // Duplo clique na tabela para editar
        tvUsuarios.setRowFactory(tv -> {
            TableRow<Usuario> row = new TableRow<>();
            row.setOnMouseClicked(evt -> {
                if (evt.getClickCount() == 2 && !row.isEmpty()) {
                    abrirEditor(row.getItem());
                }
            });
            return row;
        });
    }

    public void carregar() {
        List<Usuario> list = usuarioRepository.find(500);
        tvUsuarios.setItems(FXCollections.observableArrayList(list));
    }

    @FXML
    public void onBuscar() {
        String termo = txtBuscaUsuario.getText();
        
        if (termo == null || termo.trim().isEmpty()) {
            // Se campo vazio, carrega todos
            carregar();
        } else {
            // Busca por nome ou email
            List<Usuario> list = usuarioRepository.findByNomeOrEmail(termo.trim(), 500);
            tvUsuarios.setItems(FXCollections.observableArrayList(list));
        }
    }

    @FXML
    public void onNovoUsuario() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/novo-usuario.fxml"));
            Parent root = loader.load();

            NovoUsuarioController controller = loader.getController();
            controller.setOnSaved(this::carregar);

            Scene scene = SceneFactory.createScene(root);

            Stage stage = new Stage();
            stage.setTitle("Novo Usuário");
            stage.setScene(scene);
            stage.initOwner(btnNovoUsuario.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir formulário: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    public void onEditarUsuario() {
        Usuario usuario = tvUsuarios.getSelectionModel().getSelectedItem();
        if (usuario != null) {
            abrirEditor(usuario);
        } else {
            new Alert(Alert.AlertType.WARNING, "Selecione um usuário na tabela para editar.").showAndWait();
        }
    }

    @FXML
    public void onExcluirUsuario(){
        Usuario usuario = tvUsuarios.getSelectionModel().getSelectedItem();
        if (usuario != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmar Exclusão?");
            confirm.setHeaderText("Deseja realmente excluir este usuário?");
            confirm.setContentText("Usuário: " + usuario.getNome() + "\n\nEsta ação é permanente.");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                usuarioRepository.delete(usuario.getId());
                new Alert(Alert.AlertType.INFORMATION, "Usuário excluido com sucesso!").showAndWait();
                carregar();
            }
        } else {
            new Alert(Alert.AlertType.WARNING, "Selecione um usuário na tabela para excluir.").showAndWait();
        }
    }

    @FXML
    public void onInativarUsuario() {
        Usuario usuario = tvUsuarios.getSelectionModel().getSelectedItem();
        if (usuario != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmar Inativação");
            confirm.setHeaderText("Deseja realmente inativar este usuário?");
            confirm.setContentText("Usuário: " + usuario.getNome());

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                usuarioRepository.inactivate(usuario.getId());
                new Alert(Alert.AlertType.INFORMATION, "Usuário inativado com sucesso!").showAndWait();
                carregar();
            }
        } else {
            new Alert(Alert.AlertType.WARNING, "Selecione um usuário na tabela para inativar.").showAndWait();
        }
    }

    private void abrirEditor(Usuario usuario) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/editar-usuario.fxml"));
            Parent root = loader.load();

            EditarUsuarioController controller = loader.getController();
            controller.setUsuario(usuario);
            controller.setOnSaved(this::carregar);

            Scene scene = SceneFactory.createScene(root);

            Stage stage = new Stage();
            stage.setTitle("Editar Usuário - " + usuario.getNome());
            stage.setScene(scene);
            stage.initOwner(tvUsuarios.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Falha ao abrir editor: " + e.getMessage()).showAndWait();
        }
    }
}