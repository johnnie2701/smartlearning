package com.gemma3n.smartlearning

import android.content.Context
import android.util.Log
import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.core.proto.FunctionCall
import com.google.ai.edge.localagents.core.proto.FunctionDeclaration
import com.google.ai.edge.localagents.core.proto.FunctionResponse
import com.google.ai.edge.localagents.core.proto.GenerateContentResponse
import com.google.ai.edge.localagents.core.proto.Part
import com.google.ai.edge.localagents.core.proto.Schema
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.core.proto.Type
import com.google.ai.edge.localagents.fc.ChatSession
import com.google.ai.edge.localagents.fc.GemmaFormatter
import com.google.ai.edge.localagents.fc.GenerativeModel
import com.google.ai.edge.localagents.fc.LlmInferenceBackend
import com.google.ai.edge.localagents.fc.ModelFormatterOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.protobuf.Struct
import com.google.protobuf.Value


class GenModel( context: Context) {
    private var chatSession: ChatSession? = null
    val generativeModel by lazy { createGenerativeModel(context) }

    fun sendMessage(message: String): GenerateContentResponse {
        if (chatSession == null) {
            chatSession = generativeModel.startChat()
        }
        val response = chatSession!!.sendMessage(message)


        val message: Part= response.getCandidates(0).getContent().getParts(0)

        if (message.hasFunctionCall()) {
            val functionCall: FunctionCall = message.getFunctionCall()
            val args = functionCall.getArgs().getFieldsMap()
            var result: String? = null

            when (functionCall.getName()) {
                "getWeather" -> result = ToolsForLlm.getWeather(args.get("location")!!.getStringValue())

                "getTime" -> result = ToolsForLlm.getWeather(args.get("timezone")!!.getStringValue())
                else -> throw Exception("Function does not exist:" + functionCall.getName())
            }
            // Return the result of the function call to the model.
            val functionResponse: FunctionResponse =
                FunctionResponse.newBuilder()
                    .setName(functionCall.getName())
                    .setResponse(
                        Struct.newBuilder()
                            .putFields("result", Value.newBuilder().setStringValue(result).build())
                    )
                    .build()
            val functionResponseContent = Content.newBuilder()
                .setRole("user")
                .addParts(Part.newBuilder().setFunctionResponse(functionResponse))
                .build()
            return chatSession!!.sendMessage(functionResponseContent)
        } else if (message.hasText()) {
            Log.i("GenModel", message.getText())
        }
        return response
    }

    private fun getTool(): Tool {
        val getLanguage = FunctionDeclaration.newBuilder()
            .setName("getLanguage")
            .setDescription("Returns the device language.")
            .build()
        val getWeather = FunctionDeclaration.newBuilder()
            .setName("getWeather")
            .setDescription("Returns the weather conditions at a location.")
            .setParameters(
                Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .putProperties(
                        "location",
                        Schema.newBuilder()
                            .setType(Type.STRING)
                            .setDescription("The location for the weather report.")
                            .build()
                    )
                    .build()
            )
            .build()
        val tool = Tool.newBuilder()
            .addFunctionDeclarations(getWeather)
            .addFunctionDeclarations(getLanguage)
            .build()

        return tool
    }

     fun createGenerativeModel( context: Context): GenerativeModel {
        val formatter =
            GemmaFormatter(ModelFormatterOptions.builder().setAddPromptTemplate(true).build())

        val llmInferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath("/data/local/tmp/llm/Gemma3-1B-IT_seq128_q8_ekv4096.task")
            .setMaxTokens(2048)
            .apply { setPreferredBackend(LlmInference.Backend.GPU) }
            .build()

        val llmInference =
            LlmInference.createFromOptions(context, llmInferenceOptions)
        val llmInferenceBackend =
            LlmInferenceBackend(llmInference, formatter)

        val systemInstruction = Content.newBuilder()
            .setRole("system")
            .addParts(
                Part.newBuilder()
                    .setText("You are a helpful assistant. You have access to a set of tools. Use them whenever they are required to answer a user's question.")
            )
            .build()

        val tool = getTool()

//        Log.d("GenModel", "formatter: " + formatter.formatSystemMessage(systemInstruction, listOf(tool).toMutableList()))
//        val session = llmInferenceBackend.createSession()
//        session.addSystemMessage(systemInstruction, listOf(tool).toMutableList())
//        val content = Content.newBuilder()
//        .setRole("user")
//        .addParts(
//            Part.newBuilder()
//                .setText("what's the current weather conditions for Paris?")
//        )
//        .build()
//        session.addMessage(content)
//        val resp = session.generateResponse().get()
//        Log.d("GenModel", "responses count: " + resp.size)
//
//        for (response in resp) {
//            Log.d("GenModel", "response: " + formatter.formatContent(response))
//        }

        val model = GenerativeModel(
            llmInferenceBackend,
            systemInstruction,
            listOf(tool).toMutableList()
        )

        return model
    }
}

internal object ToolsForLlm {
    fun getWeather(location: String?): String {
        return "Cloudy, 56°F"
    }

    fun getTime(timezone: String): String {
        return "7:00 PM " + timezone
    }
}