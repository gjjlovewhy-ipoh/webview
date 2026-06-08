import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestHttp {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://t.freetv.fun/m3u/playlist_all_original.m3u");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36");
            
            int code = connection.getResponseCode();
            System.out.println("Response Code: " + code);
            
            if (code >= 200 && code <= 299) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    count++;
                }
                reader.close();
                System.out.println("Total lines read: " + count);
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
