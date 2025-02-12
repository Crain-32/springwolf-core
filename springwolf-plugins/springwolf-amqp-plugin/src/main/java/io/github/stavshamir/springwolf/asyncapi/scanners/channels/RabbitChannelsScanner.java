package io.github.stavshamir.springwolf.asyncapi.scanners.channels;

import com.asyncapi.v2.binding.ChannelBinding;
import com.asyncapi.v2.binding.OperationBinding;
import com.asyncapi.v2.binding.amqp.AMQPChannelBinding;
import com.asyncapi.v2.binding.amqp.AMQPOperationBinding;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringValueResolver;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class RabbitChannelsScanner extends AbstractChannelScanner<RabbitListener>
        implements ChannelsScanner, EmbeddedValueResolverAware {

    private final Map<String, Binding> bindingsMap;
    private StringValueResolver resolver;

    public RabbitChannelsScanner(List<Binding> bindings) {
        bindingsMap = bindings.stream()
                .filter(Binding::isDestinationQueue)
                .collect(Collectors.toMap(Binding::getDestination, Function.identity()));
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected Class<RabbitListener> getListenerAnnotationClass() {
        return RabbitListener.class;
    }

    @Override
    protected String getChannelName(RabbitListener annotation) {
        if (annotation.queues().length > 0) {
            return getChannelNameFromQueues(annotation);
        }

        return getChannelNameFromBindings(annotation);
    }

    private String getChannelNameFromQueues(RabbitListener annotation) {
        return Arrays.stream(annotation.queues())
                .map(resolver::resolveStringValue)
                .peek(queue -> log.debug("Resolved queue name: {}", queue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No queue name was found in @RabbitListener annotation"));
    }

    private String getChannelNameFromBindings(RabbitListener annotation) {
        return Arrays.stream(annotation.bindings())
                .map(binding -> binding.value().name())
                .map(resolver::resolveStringValue)
                .peek(queue -> log.debug("Resolved queue name: {}", queue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No queue name was found in @RabbitListener annotation"));
    }

    @Override
    protected Map<String, ? extends ChannelBinding> buildChannelBinding(RabbitListener annotation) {
        AMQPChannelBinding.ExchangeProperties exchangeProperties = new AMQPChannelBinding.ExchangeProperties();
        exchangeProperties.setName(getExchangeName(annotation));
        return ImmutableMap.of("amqp", AMQPChannelBinding.builder()
                .is("routingKey")
                .exchange(exchangeProperties)
                .build());
    }

    private String getExchangeName(RabbitListener annotation) {
        String exchangeName = Stream.of(annotation.bindings())
                .map(binding -> binding.exchange().name())
                .findFirst()
                .orElse(null);
        if (exchangeName == null && bindingsMap.containsKey(getChannelName(annotation))) {
            Binding binding = bindingsMap.get(getChannelName(annotation));
            exchangeName = binding.getExchange();
        }
        if (exchangeName == null) {
            exchangeName = "";
        }

        return exchangeName;
    }

    @Override
    protected Map<String, ? extends OperationBinding> buildOperationBinding(RabbitListener annotation) {
        return ImmutableMap.of("amqp", AMQPOperationBinding.builder()
                .cc(getRoutingKeys(annotation))
                .build());
    }

    private List<String> getRoutingKeys(RabbitListener annotation) {
        /*
            The routing key is taken from the binding. As the key field in the @QueueBinding can be an empty array,
            it is set as an empty String in that case.
            Also in the case when there is no binding for the queue present at all, it means it uses the fact that
            RabbitMQ automatically binds default exchange to a queue with queue's name as a routing key.
         */
        List<String> routingKeys = Stream.of(annotation.bindings())
                .map(binding -> {
                    if (binding.key().length == 0) {
                        return Collections.singletonList("");
                    }

                    return Arrays.asList(binding.key());
                })
                .findFirst()
                .orElse(null);
        if (routingKeys == null && bindingsMap.containsKey(getChannelName(annotation))) {
            Binding binding = bindingsMap.get(getChannelName(annotation));
            routingKeys = Collections.singletonList(binding.getRoutingKey());
        }

        String exchangeName = getExchangeName(annotation);
        if (exchangeName.isEmpty() && routingKeys == null) {
            routingKeys = Collections.singletonList(getChannelName(annotation));
        }

        return routingKeys;
    }

    @Override
    protected Class<?> getPayloadType(Method method) {
        String methodName = String.format("%s::%s", method.getDeclaringClass().getSimpleName(), method.getName());
        log.debug("Finding payload type for {}", methodName);

        Class<?>[] parameterTypes = method.getParameterTypes();
        switch (parameterTypes.length) {
            case 0:
                throw new IllegalArgumentException("Listener methods must not have 0 parameters: " + methodName);
            case 1:
                return parameterTypes[0];
            default:
                return getPayloadType(parameterTypes, method.getParameterAnnotations(), methodName);
        }
    }

    private Class<?> getPayloadType(Class<?>[] parameterTypes, Annotation[][] parameterAnnotations, String methodName) {
        int payloadAnnotatedParameterIndex = getPayloadAnnotatedParameterIndex(parameterAnnotations);

        if (payloadAnnotatedParameterIndex == -1) {
            String msg = "Multi-parameter RabbitListener methods must have one parameter annotated with @Payload, "
                    + "but none was found: "
                    + methodName;

            throw new IllegalArgumentException(msg);
        }

        return parameterTypes[payloadAnnotatedParameterIndex];
    }

    private int getPayloadAnnotatedParameterIndex(Annotation[][] parameterAnnotations) {
        for (int i = 0, length = parameterAnnotations.length; i < length; i++) {
            Annotation[] annotations = parameterAnnotations[i];
            boolean hasPayloadAnnotation = Arrays.stream(annotations)
                    .anyMatch(annotation -> annotation instanceof Payload);

            if (hasPayloadAnnotation) {
                return i;
            }
        }

        return -1;
    }

}
