package com.lunchee.fizzbuzz.game

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY
import org.springframework.http.MediaType.APPLICATION_STREAM_JSON
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.requestParameters
import org.springframework.restdocs.snippet.Attributes.key
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.web.reactive.server.WebTestClient

@ExperimentalCoroutinesApi
@WebFluxTest(controllers = [GameController::class])
@AutoConfigureRestDocs
open class GameControllerIT {

    @MockBean
    private lateinit var game: Game

    @Autowired
    private lateinit var testClient: WebTestClient

    @Test
    fun `when playing game, should return answers as a stream`() {
        given(game.play(CountToNumber(5))).willReturn(answersFlux("1", "2", "Fizz", "4", "Buzz"))

        testClient
            .get().uri("/game?countTo=5").accept(APPLICATION_STREAM_JSON).exchange()
            .expectStatus().isOk
            .expectHeader().contentType("application/stream+json;charset=UTF-8")
            .expectBodyList(Answer::class.java)
            .hasSize(5)
            .contains(*answers("1", "2", "Fizz", "4", "Buzz"))
            .consumeWith<WebTestClient.ListBodySpec<Answer>>(
                document(
                    "game-start",
                    requestParameters(
                        parameterWithName("countTo")
                            .description("Number to play up to, inclusive.")
                            .attributes(key("constraints").value("Must be greater than zero"))
                    ),
                    responseFields(
                        fieldWithPath("value").description("Answer of a Player")
                    )
                )
            )
    }

    private fun answersFlux(vararg answers: String): Flow<Answer> {
        return answers.asSequence()
            .map { Answer(it) }
            .asFlow()
    }

    private fun answers(vararg answers: String): Array<Answer> {
        return answers.map { Answer(it) }.toTypedArray()
    }

    @Test
    fun `when playing game, should return 422 Unprocessable Entity if Count To is less than 1`() {
        testClient.get().uri("/game?countTo=0").accept(APPLICATION_STREAM_JSON).exchange()
            .expectStatus().isEqualTo(UNPROCESSABLE_ENTITY)
            .expectBody()
            .consumeWith(
                document(
                    "game-start-illegal-count",
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("timestamp").description("Timestamp of an Error"),
                        fieldWithPath("path").description("Requested Path"),
                        fieldWithPath("status").description("HTTP Response Status Value"),
                        fieldWithPath("error").description("HTTP Response Status Name"),
                        fieldWithPath("message").description("Error Message")
                    )
                )
            )
    }

    @Test
    fun `when requesting an answer for a single number, should return a stream of one answer`() {
        // given game will return "Buzz Fizz?" for 15

        testClient
            .get().uri("/game/answers?numbers=15").accept(APPLICATION_STREAM_JSON).exchange()
            .expectStatus().isOk
            .expectHeader().contentType("application/stream+json;charset=UTF-8")
            .expectBodyList(Answer::class.java)
            .hasSize(1)
            .contains(*answers("Buzz Fizz?"))
            .consumeWith<WebTestClient.ListBodySpec<Answer>>(
                document(
                    "get-single-answer",
                    requestParameters(
                        parameterWithName("numbers")
                            .description("One or more numbers delimited by comma to get answers for")
                    ),
                    responseFields(
                        fieldWithPath("value").description("Answer for a number")
                    )
                )
            )

    }

    @Test
    fun `when requesting answers for multiple numbers, should return a stream of as much answers as was requested`() {
        // given game will return ["1", "Buzz", "Fizz", 10] for [1, 5, 3, 10]

        testClient
            .get().uri("/game/answers?numbers=1,5,3,10").accept(APPLICATION_STREAM_JSON).exchange()
            .expectStatus().isOk
            .expectHeader().contentType("application/stream+json;charset=UTF-8")
            .expectBodyList(Answer::class.java)
            .hasSize(4)
            .contains(*answers("1", "Buzz", "Fizz", "10"))
            .consumeWith<WebTestClient.ListBodySpec<Answer>>(document("get-multiple-answers"))
    }

}