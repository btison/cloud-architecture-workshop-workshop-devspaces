// camel-k: language=groovy
// camel-k: dependency=camel:http
// camel-k: property=file:cashback-customer-connector.properties
// camel-k: config=secret:kafka-client-secret

from("kafka:{{kafka.customer.topic.name}}?groupId={{kafka.customer.consumer.group}}&autoOffsetReset=earliest")
    .unmarshal().json()
    .log(LoggingLevel.INFO, 'Customer event received: ${body}')
    .filter().jsonpath('$..[?(@.op =~ /d|t|m/)]')
        .log(LoggingLevel.INFO, "Filtering out change event which is not 'create', 'read' or or 'update'")
        .stop()
        .end()
    .choice()
    .when().jsonpath('$..[?(@.op =~ /c|r/)]')
        .log(LoggingLevel.INFO, 'Create customer ${body[after][user_id]}')
        .setHeader(Exchange.HTTP_METHOD, constant("POST"))
    .when().jsonpath('$..[?(@.op == "u")]')
        .log(LoggingLevel.INFO, 'Update customer ${body[after][user_id]}')
        .setHeader(Exchange.HTTP_METHOD, constant("PUT"))
    .end()
    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
    .setHeader(Exchange.HTTP_PATH, simple('customer/${body[after][user_id]}'))
    .process {
        def customerName = it.in.body["after"]["first_name"] + ' ' + it.in.body["after"]["last_name"]
        it.in.body = [customerName: customerName]
    }
    .marshal().json()
    .to("{{cashback-service.url}}");
