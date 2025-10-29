package eu.hhmmss.app.converter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HhmmssDto {

    private Map<String, String> meta = new HashMap<>();
    private Map<Integer, Pair<String, Double>> tasks = new HashMap<>();

}
