package marvin.com.br.tiraentulho.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import lombok.Data;

@Entity(tableName = "config_model")
@Data
public class ConfigModel {

    @PrimaryKey(autoGenerate = true)
    public Long id;
    public Integer tenant_id;
    public String motorista;
    public String cpf_motorista;
    public String veiculo;
    public String sit;
}
