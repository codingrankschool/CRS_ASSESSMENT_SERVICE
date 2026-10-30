package com.codingrank.assessment.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "TestCase")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCase {

    @Id
    private String id;

    @Indexed
    private String questionId;

    private String input;
    private String output;
    private String explanation;

    @Builder.Default
    private boolean isHidden = false;

    private int orderIndex;

    public String getExpectedOutput() {
        return output;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.output = expectedOutput;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public void setHidden(boolean hidden) {
        this.isHidden = hidden;
    }
}
