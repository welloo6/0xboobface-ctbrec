package ctbrec;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class LoggingInterceptor implements Interceptor {

    private static final transient Logger LOG = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public Response intercept(Chain chain) throws IOException {
        long t1 = System.nanoTime();
        Request request = chain.request();
        LOG.debug("OkHttp Sending request {} on {}\n{}", request.url(), chain.connection(), request.headers());
        if(request.method().equalsIgnoreCase("POST")) {
            LOG.debug("Body: {}", request.body().toString());
        }
        Response response = chain.proceed(request);
        long t2 = System.nanoTime();
        LOG.debug("OkHttp Received response for {} in {}\n{}", response.request().url(), (t2 - t1) / 1e6d, response.headers());
        return response;
    }
}
