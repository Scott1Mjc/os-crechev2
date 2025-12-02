package br.com.creche.repository;

import br.com.creche.infra.DB;
import br.com.creche.model.OrdemServico;
import br.com.creche.model.Usuario;

import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class UsuarioRepository {

    public List<Usuario> find(int limit) {
        String sql = """
            select
            id, nome, email, perfil, ativo, criado_em, atualizado_em
            from usuarios
            order by nome asc
            limit ?
        """;

        List<Usuario> list = new ArrayList<>();
        try (var conn = DB.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Usuario user = new Usuario();
                    user.setId(rs.getLong("id"));
                    user.setNome(rs.getString("nome"));
                    user.setEmail(rs.getString("email"));
                    user.setPerfil(rs.getString("perfil"));
                    user.setAtivo(rs.getBoolean("ativo"));

                    list.add(user);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao listar usuários.", e);
        }
        return list;
    }

    public List<Usuario> findByNomeOrEmail(String searchTerm, int limit) {
        String sql = """
            select
            id, nome, email, perfil, ativo, criado_em, atualizado_em
            from usuarios
            where lower(nome) like ? or lower(email) like ?
            order by nome asc
            limit ?
        """;

        List<Usuario> list = new ArrayList<>();
        String term = "%" + searchTerm.toLowerCase(Locale.ROOT) + "%";
        
        try (var conn = DB.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, term);
            ps.setString(2, term);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Usuario user = new Usuario();
                    user.setId(rs.getLong("id"));
                    user.setNome(rs.getString("nome"));
                    user.setEmail(rs.getString("email"));
                    user.setPerfil(rs.getString("perfil"));
                    user.setAtivo(rs.getBoolean("ativo"));

                    list.add(user);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar usuários.", e);
        }
        return list;
    }

    public Optional<Usuario> findByEmail(String email) {
        String sql = "select id, nome, email, perfil, senha_hash, ativo from usuarios where email = ? and ativo = true";
        try (var conn = DB.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Usuario u = new Usuario();
                    u.setId(rs.getLong("id"));
                    u.setNome(rs.getString("nome"));
                    u.setEmail(rs.getString("email"));
                    u.setPerfil(rs.getString("perfil"));
                    u.setSenhaHash(rs.getString("senha_hash"));
                    u.setAtivo(rs.getBoolean("ativo"));
                    return Optional.of(u);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar usuário", e);
        }
        return Optional.empty();
    }

    public void insert(Usuario usuario) {
        String sql = """
            insert into usuarios (nome, email, perfil, ativo, senha_hash)
            values (?, ?, ?, ?, ?)
            returning id, criado_em
        """;

        try (var conn = DB.getConnection();
             var ps = conn.prepareStatement(sql)) {

            ps.setString(1, usuario.getNome());
            ps.setString(2, usuario.getEmail());
            ps.setString(3, usuario.getPerfil());
            ps.setBoolean(4, usuario.isAtivo());
            ps.setString(5, usuario.getSenhaHash());

            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    usuario.setId(rs.getLong("id"));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao inserir usuário: " + e.getMessage(), e);
        }
    }

    public void update(Usuario usuario) {
        // Se a senha foi alterada, atualiza também
        String sql;
        if (usuario.getSenhaHash() != null && !usuario.getSenhaHash().isEmpty()) {
            sql = """
                update usuarios set
                    nome = ?, email = ?, perfil = ?, ativo = ?, senha_hash = ?, atualizado_em = now()
                where id = ?
            """;
        } else {
            sql = """
                update usuarios set
                    nome = ?, email = ?, perfil = ?, ativo = ?, atualizado_em = now()
                where id = ?
            """;
        }

        try (var conn = DB.getConnection();
             var ps = conn.prepareStatement(sql)) {

            ps.setString(1, usuario.getNome());
            ps.setString(2, usuario.getEmail());
            ps.setString(3, usuario.getPerfil());
            ps.setBoolean(4, usuario.isAtivo());
            
            if (usuario.getSenhaHash() != null && !usuario.getSenhaHash().isEmpty()) {
                ps.setString(5, usuario.getSenhaHash());
                ps.setLong(6, usuario.getId());
            } else {
                ps.setLong(5, usuario.getId());
            }

            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao atualizar usuário: " + e.getMessage(), e);
        }
    }

    public void delete(Long usuarioId) {
        String sql = "DELETE FROM usuarios WHERE id = ?";

        try (var conn = DB.getConnection();
             var ps = conn.prepareStatement(sql)) {

            ps.setLong(1, usuarioId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao excluir usuário: " + e.getMessage(), e);
        }
    }


    public void inactivate(Long usuarioId) {
        String sql = "update usuarios set ativo = false, atualizado_em = now() where id = ?";

        try (var conn = DB.getConnection();
             var ps = conn.prepareStatement(sql)) {

            ps.setLong(1, usuarioId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao inativar usuário: " + e.getMessage(), e);
        }
    }
}