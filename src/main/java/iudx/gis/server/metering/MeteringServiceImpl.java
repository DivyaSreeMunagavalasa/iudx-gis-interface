package iudx.gis.server.metering;

import static iudx.gis.server.metering.util.Constants.EXCHANGE_NAME;
import static iudx.gis.server.metering.util.Constants.ROUTING_KEY;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import iudx.gis.server.common.Response;
import iudx.gis.server.databroker.DataBrokerService;
import iudx.gis.server.metering.util.QueryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MeteringServiceImpl implements MeteringService {

  private static final Logger LOGGER = LogManager.getLogger(MeteringServiceImpl.class);
  private final QueryBuilder queryBuilder = new QueryBuilder();
  private final DataBrokerService dataBrokerService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private JsonObject config;

  public MeteringServiceImpl(DataBrokerService dataBrokerService, JsonObject config) {
    this.dataBrokerService = dataBrokerService;
    this.config = config;
  }

  @Override
  public MeteringService insertMeteringValuesInRmq(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler) {

    JsonObject writeMessage = queryBuilder.buildMessageForRmq(request, config);

    dataBrokerService.publishMessage(
        writeMessage,
        EXCHANGE_NAME,
        ROUTING_KEY,
        rmqHandler -> {
          if (rmqHandler.succeeded()) {
            handler.handle(Future.succeededFuture());
          } else {
            LOGGER.error(rmqHandler.cause());
            try {
              Response resp =
                  objectMapper.readValue(rmqHandler.cause().getMessage(), Response.class);
              LOGGER.debug("response from rmq " + resp);
              handler.handle(Future.failedFuture(resp.toString()));
            } catch (JsonProcessingException e) {
              LOGGER.error("Failure message not in format [type,title,detail]");
              handler.handle(Future.failedFuture(e.getMessage()));
            }
          }
        });
    return this;
  }
}
