package br.com.creche.repository;

import br.com.creche.infra.DB;
import br.com.creche.model.OrdemServico;
import br.com.creche.model.Usuario;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.sql.Types;

public class OrdemServicoRepository {

    public List<OrdemServico> findRecentes(int limit) {
        String sql = """
            select os.id, os.numero, os.titulo, os.descricao, os.categoria, 
                   os.prioridade, os.status, os.solicitante, os.data_abertura, 
                   os.prazo, os.data_conclusao, os.criado_por, os.atribuido_para, 
                   os.observacoes,
                   u2.nome as atribuido_para_nome,
                   u2.email as atribuido_para_email
            from ordens_servico os
            left join usuarios u2 on os.atribuido_para = u2.id
            order by os.data_abertura desc
            limit ?
        """;

        List<OrdemServico> list = new ArrayList<>();
        try (var conn = DB.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrdemServico os = new OrdemServico();
                    os.setId(rs.getLong("id"));
                    os.setNumero(rs.getString("numero"));
                    os.setTitulo(rs.getString("titulo"));
                    os.setDescricao(rs.getString("descricao"));
                    os.setCategoria(rs.getString("categoria"));
                    os.setPrioridade(rs.getString("prioridade"));
                    os.setStatus(rs.getString("status"));
                    os.setSolicitante(rs.getString("solicitante"));

                    // Datas
                    os.setDataAbertura(timestampToOffsetDateTime(rs.getTimestamp("data_abertura")));
                    os.setPrazo(timestampToOffsetDateTime(rs.getTimestamp("prazo")));
                    os.setDataConclusao(timestampToOffsetDateTime(rs.getTimestamp("data_conclusao")));

                    // Relacionamentos
                    os.setCriadoPor(rs.getLong("criado_por"));

                    // Criar objeto Usuario para atribuidoPara
                    Long atribuidoParaId = rs.getLong("atribuido_para");
                    if (atribuidoParaId != null && atribuidoParaId > 0) {
                        Usuario responsavel = new Usuario();
                        responsavel.setId(atribuidoParaId);
                        responsavel.setNome(rs.getString("atribuido_para_nome"));
                        os.setAtribuidoPara(responsavel);
                    } else {
                        os.setAtribuidoPara(null);
                    }
                    os.setObservacoes(rs.getString("observacoes"));


                    list.add(os);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao listar O.S.", e);
        }
        return list;
    }

    private OffsetDateTime timestampToOffsetDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant().atOffset(OffsetDateTime.now().getOffset()) : null;
    }

    public void insert(OrdemServico os, Long usuarioId) {
        
    	String sql = """
            insert into ordens_servico
            (numero, titulo, descricao, categoria, prioridade, status, solicitante, prazo, criado_por, atribuido_para, observacoes)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            returning id, data_abertura
        """;
        
        try (var conn = DB.getConnection();
             var ps = conn.prepareStatement(sql)) {

            int i = 1;
            ps.setString(i++, os.getNumero());
            ps.setString(i++, os.getTitulo());
            ps.setString(i++, os.getDescricao());
            ps.setString(i++, os.getCategoria());
            ps.setString(i++, os.getPrioridade());
            ps.setString(i++, os.getStatus());
            ps.setString(i++, os.getSolicitante());

            // prazo (timestamptz)
            if (os.getPrazo() != null) {
                ps.setObject(i++, os.getPrazo()); // OffsetDateTime/Instant/Timestamp (JDBC 4.2)
            } else {
                ps.setNull(i++, Types.TIMESTAMP_WITH_TIMEZONE);
            }

            // criado_por (BIGINT)
            if (usuarioId != null) {
                ps.setLong(i++, usuarioId);
            } else {
                ps.setNull(i++, Types.BIGINT);
            }

            // atribuido_para (BIGINT) -> usa o ID do usuário
            Long atribuidoId = (os.getAtribuidoPara() != null) ? os.getAtribuidoPara().getId() : null;
            if (atribuidoId != null) {
                ps.setLong(i++, atribuidoId);
            } else {
                ps.setNull(i++, Types.BIGINT);
            }

            ps.setString(i++, os.getObservacoes());

            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    os.setId(rs.getLong("id"));
                    os.setDataAbertura(rs.getObject("data_abertura", java.time.OffsetDateTime.class));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao inserir O.S.: " + e.getMessage(), e);
        }
    }

    public void update(OrdemServico os) {
        String sql = """
            update ordens_servico set
                numero = ?, titulo = ?, descricao = ?, categoria = ?, prioridade = ?,
                status = ?, solicitante = ?, prazo = ?, atribuido_para = ?, observacoes = ?,
                data_conclusao = case when ? = 'CONCLUIDA' and data_conclusao is null then now()
                                      when ? <> 'CONCLUIDA' then null
                                      else data_conclusao end
            where id = ?
        """;
        
        try (var conn = DB.getConnection();
             var ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, os.getNumero());
            ps.setString(i++, os.getTitulo());
            ps.setString(i++, os.getDescricao());
            ps.setString(i++, os.getCategoria());
            ps.setString(i++, os.getPrioridade());
            ps.setString(i++, os.getStatus());
            ps.setString(i++, os.getSolicitante());

            if (os.getPrazo() != null) ps.setObject(i++, os.getPrazo());
            else ps.setNull(i++, Types.TIMESTAMP_WITH_TIMEZONE);

            Long atribuidoId = (os.getAtribuidoPara() != null) ? os.getAtribuidoPara().getId() : null;
            if (atribuidoId != null) ps.setLong(i++, atribuidoId);
            else ps.setNull(i++, Types.BIGINT);

            ps.setString(i++, os.getObservacoes());

            ps.setString(i++, os.getStatus());
            ps.setString(i++, os.getStatus());

            ps.setLong(i++, os.getId());

            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao atualizar O.S.: " + e.getMessage(), e);
        }
    }

    public void delete(Long osId) {
        try (var conn = DB.getConnection()) {
            conn.setAutoCommit(false);

            // Apagar histórico primeiro (chave estrangeira)
            String sqlHistorico = "DELETE FROM os_historico WHERE os_id = ?";
            try (var ps = conn.prepareStatement(sqlHistorico)) {
                ps.setLong(1, osId);
                ps.executeUpdate();
            }

            // Apagar a O.S.
            String sqlOS = "DELETE FROM ordens_servico WHERE id = ?";
            try (var ps = conn.prepareStatement(sqlOS)) {
                ps.setLong(1, osId);
                ps.executeUpdate();
            }

            conn.commit();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao apagar O.S.: " + e.getMessage(), e);
        }
    }
}