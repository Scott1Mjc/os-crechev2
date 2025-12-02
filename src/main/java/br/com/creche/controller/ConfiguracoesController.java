package br.com.creche.controller;

import br.com.creche.ui.ThemeManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;

import java.util.prefs.Preferences;

public class ConfiguracoesController implements DashboardController.RequiresAuthService {
    @FXML
    private TextField txtOrg;
    @FXML
    private ChoiceBox<String> cbTema;
    @FXML
    private ChoiceBox<String> cbIdioma;
    @FXML
    private CheckBox chkEmail;
    @FXML
    private Text txtSalvoSucesso;

    private static final String PREFS_NODE = "br.com.creche";
    private static final String PREF_TEMA = "tema"; // SISTEMA|CLARO|ESCURO

    private ThemeManager.Theme temaSelecionado = ThemeManager.Theme.SISTEMA;

    @FXML
    public void initialize() {
        cbTema.setItems(FXCollections.observableArrayList("Sistema", "Claro", "Escuro"));
        carregarPreferencias();
        // corrigido erro em que a o tema alterava antes de clicar no botão aplicar
        cbTema.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            temaSelecionado = map(n);
        });
    }

    @FXML
    public void onAplicarTema() {
        temaSelecionado = map(cbTema.getValue());
        aplicarTemaNaCenaAtual();
        salvarPreferencias();
    }

    private void aplicarTemaNaCenaAtual() {
        // pega a Scene através de qualquer nó da tela
        Scene scene = cbTema.getScene();
        ThemeManager.apply(scene, temaSelecionado);
    }

    private void carregarPreferencias() {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
        String tema = prefs.get(PREF_TEMA, "SISTEMA");
        temaSelecionado = ThemeManager.Theme.valueOf(tema);
        cbTema.getSelectionModel().select(unmap(temaSelecionado));
    }

    private void salvarPreferencias() {
        Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
        prefs.put(PREF_TEMA, temaSelecionado.name());
    }

    private ThemeManager.Theme map(String s) {
        if (s == null) return ThemeManager.Theme.SISTEMA;
        return switch (s.toUpperCase()) {
            case "CLARO" -> ThemeManager.Theme.CLARO;
            case "ESCURO" -> ThemeManager.Theme.ESCURO;
            default -> ThemeManager.Theme.SISTEMA;
        };
    }

    private String unmap(ThemeManager.Theme t) {
        return switch (t) {
            case CLARO -> "Claro";
            case ESCURO -> "Escuro";
            case SISTEMA -> "Sistema";
        };
    }

    // RequiresAuthService (se precisar do AuthService)
    @Override
    public void setAuthService(br.com.creche.service.AuthService authService) { /* opcional */ }
}