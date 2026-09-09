package marvin.com.br.tiraentulho.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import lombok.Data;

@Entity(tableName = "percurso_model")
@Data
public class PercursoModel {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String uuid_rota;
    public Long id_rota;
    public Integer tenant_id;
    public Double lat;
    public Double lon;
    public String data;
    public String hora;
    public Float precisao;
    public Float velocidade;
    public Integer sincronizado;
    public String sit;
}
