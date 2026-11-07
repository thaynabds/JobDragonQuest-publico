package dev.jobdragonquest.profiles;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Resposta pronta para uma pergunta frequente de formulário (ex.: "Por que você quer trabalhar aqui?"). */
@Embeddable
public class ProfileAnswer {

    @Column(nullable = false, length = 500)
    private String question;

    @Column(nullable = false, length = 4000)
    private String answer;

    protected ProfileAnswer() {
    }

    public ProfileAnswer(String question, String answer) {
        this.question = question;
        this.answer = answer;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }
}
