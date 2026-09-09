package marvin.com.br.tiraentulho.repository;

import marvin.com.br.tiraentulho.model.MotoristaModel;
import marvin.com.br.tiraentulho.model.SincronizacaoRequest;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface RetroServiceInterface {


    @POST("/app_entulho/sincronizar_rotas")
    Call<ResponseBody> sincronizarRotas(@Body SincronizacaoRequest request);

    @GET("/app_obras/pegar_operador_cpf")
    Call<MotoristaModel> pegar_motorista_cpf(@Query("cpf") String cpf);
}
