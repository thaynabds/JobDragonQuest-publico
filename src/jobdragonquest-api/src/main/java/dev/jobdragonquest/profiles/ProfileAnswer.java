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

    @Column(length = 60)
    private String category;

    @Column(name = "updated_on")
    private java.time.LocalDate updatedOn;

    protected ProfileAnswer() {
    }

    public ProfileAnswer(String question, String answer) {
        this.question = question;
        this.answer = answer;
    }

    ProfileAnswer details(String newCategory, java.time.LocalDate date) {
        this.category = newCategory;
        this.updatedOn = date;
        return this;
    }

    public String getCategory() {
        return category;
    }

    public java.time.LocalDate getUpdatedOn() {
        return updatedOn;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }
}
