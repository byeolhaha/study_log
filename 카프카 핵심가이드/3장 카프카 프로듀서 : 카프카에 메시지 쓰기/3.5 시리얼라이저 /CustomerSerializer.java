package com.producer.demo;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

import java.nio.ByteBuffer;
import java.util.Map;

public class CustomerSerializer implements Serializer<Customer> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // nothing to configure
    }

    @Override
    /**
     We are serializing Customer as:
     4 byte int representing customerId
     4 byte int representing length of customerName in UTF-8 bytes ( 0 if name is Null)
     N bytes representing customerName in UTF-8
     **/
    public byte[] serialize(String s, Customer data) {
        try {
            byte[] serializedName;
            int stringSize;
            if(data == null) {
                return null;
            }
            if(data.getCustomerName() != null) {
                serializedName = data.getCustomerName().getBytes("UTF-8");
                stringSize = serializedName.length;
            }else{
                serializedName = new byte[0];
                stringSize = 0;
            }

            ByteBuffer buffer = ByteBuffer.allocate(4 + 4+ stringSize);
            buffer.putInt(data.getCustomerID());
            buffer.putInt(stringSize);
            buffer.put(serializedName);

            return buffer.array();
        }catch (Exception e){
            throw new SerializationException("Error When serializing Customer", e);
        }
    }
    //코드에 취약점 존재
    // 1. 고객이 너무 많아져서 CustomerID를 int에서 Long으로 바꿔야 하마 필드를 새롭게 추가해야할 경우 기존 형식과 새 형식 사이의 호환성을 유지
    //    해야하는 심각한 상황 -> 서로 다른 버전의 직렬화/비직렬화 로직을 디버깅하는 것은 상당히 어려운 작업
    // 2. 더 심각한 문제는 만약 같은 회사의 여러 팀에서 Customer 데이터를 카프카로 쓰는 작업을 수행하고 있다면 모두가 같은 로직을 사용하고 있기 때문에
    //    코드를 동시에 변경해야하는 상황이 발생한다.
    // 3. 마지막으로 내가 생각해 본 것인데 구버전과 신버전이 분명 어느 순간에는 동시에 있을텐데 이를 관리하기 위한 코드를 위해서 두 개의 형식을 모두 만들어 놓아야 한든 것
    // => 이러한 이유를 범용 라이브러리를 사용한다고 한다.

    @Override
    public void close() {
        // nothing to close
    }
}
