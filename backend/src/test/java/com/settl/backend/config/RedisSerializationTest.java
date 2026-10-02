package com.settl.backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.settl.backend.settlement.dto.GroupBalanceResponse;
import com.settl.backend.settlement.dto.UserBalanceDto;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSerializationTest {

    @Test
    void testRecordSerializationAndDeserialization() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserBalanceDto userBalance = new UserBalanceDto(
                userId, "Test User", "test@example.com",
                BigDecimal.TEN, "IS_OWED", BigDecimal.valueOf(50), BigDecimal.valueOf(40)
        );
        GroupBalanceResponse response = new GroupBalanceResponse(
                groupId, "Trip", "USD", BigDecimal.valueOf(100), List.of(userBalance)
        );

        byte[] bytes = serializer.serialize(response);
        String json = new String(bytes);
        System.out.println("Serialized JSON: " + json);

        Object deserialized = serializer.deserialize(bytes);
        assertThat(deserialized).isInstanceOf(GroupBalanceResponse.class);
        GroupBalanceResponse result = (GroupBalanceResponse) deserialized;
        assertThat(result.groupId()).isEqualTo(groupId);
        assertThat(result.balances()).hasSize(1);
        assertThat(result.balances().get(0)).isInstanceOf(UserBalanceDto.class);
        assertThat(result.balances().get(0).displayName()).isEqualTo("Test User");
    }
}
