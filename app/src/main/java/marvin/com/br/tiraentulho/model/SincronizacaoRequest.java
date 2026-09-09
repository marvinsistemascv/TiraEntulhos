package marvin.com.br.tiraentulho.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SincronizacaoRequest {

    private List<RotaModel> rotas;
    private List<PercursoModel> percursos;

    public SincronizacaoRequest(List<RotaModel> rotas,
                                List<PercursoModel> percursos) {
        this.rotas = rotas;
        this.percursos = percursos;
    }

    public List<RotaModel> getRotas() {
        return rotas;
    }

    public List<PercursoModel> getPercursos() {
        return percursos;
    }
}

