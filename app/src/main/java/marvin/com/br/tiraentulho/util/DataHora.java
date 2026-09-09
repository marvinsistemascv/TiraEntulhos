package marvin.com.br.tiraentulho.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

public class DataHora {

    public static String data_atual() {
        String data;
        Date dataSistema = new Date();
        SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd");
        data = formato.format(dataSistema);
        return data;
    }

    public static String pegar_hora() {
        // hora
        String hora;
        Date dataSistema = new Date();
        SimpleDateFormat formato = new SimpleDateFormat("HH:mm:ss");
        hora = formato.format(dataSistema);
        return hora;
    }

    public static String formatarData(String data) {
        try {

            if (data != null && !data.equals("")) {

                String dia, mes, ano;
                String dataCadastro = data;
                String[] textoSeparado = dataCadastro.split(Pattern.quote("-"));
                dia = textoSeparado[2];
                mes = textoSeparado[1];
                ano = textoSeparado[0];
                return (dia + "/" + mes + "/" + ano);
            } else {
                return "--/--/----";
            }
        } catch (Exception e) {
            System.out.println("erro na data " + e);
            return "--/--/----";
        }
    }
}
