package com.neontides.nativeapp.ai

import com.neontides.nativeapp.model.AiDialogueResult

data class BaseDialogueTestRecord(
    val timestampEpochMs: Long,
    val characterId: String,
    val characterName: String,
    val question: String,
    val affection: Int,
    val attraction: Int,
    val trust: Int,
    val stage: String,
    val modelName: String,
    val backendLabel: String,
    val firstTextMs: Long?,
    val rawStreamedReply: String,
    val streamedReply: String,
    val elapsedMs: Long,
    val preparationDiagnostic: String,
    val resourceDiagnostic: String,
    val changeConfirmed: Boolean? = null,
    val result: AiDialogueResult
)
