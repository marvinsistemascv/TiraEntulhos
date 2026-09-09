package marvin.com.br.tiraentulho.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import lombok.Data;

@Entity(tableName = "motorista_model")
@Data
public class MotoristaModel {

    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String nome;
    public String cpf;
    public Integer tenant_id;

}