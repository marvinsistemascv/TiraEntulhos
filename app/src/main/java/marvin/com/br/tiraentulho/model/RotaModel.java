package marvin.com.br.tiraentulho.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import lombok.Data;

@Entity(tableName = "rota_model")
@Data
public class RotaModel {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    public String uuid_rota;

    public Integer cod_veiculo;
    public Integer cod_motorista;

    public String nome_motorista;
    public String motorista;

    public String veiculo;

    public String nome_rota;
    public String bairro;

    public String data_rota;
    public String hora_rota;

    public Double km_percorrido;

    public String sit;
    public Integer sincronizado;

    public String rua_icinio;
    public String rua_final;

    public String bairro_inicio;
    public String bairro_final;

    public String cidade_inicio;
    public String cidade_final;

    public Double lat_inicio;
    public Double lon_inicio;

    public Double lat_final;
    public Double lon_final;

    public Integer tenant_id;
}
