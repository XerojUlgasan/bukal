package com.example.bukal.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bukal.ui.checking.CheckingScreen
import com.example.bukal.ui.checking.CheckingUiState
import com.example.bukal.ai.QuestionAnswer
import com.example.bukal.ai.QuizEvaluationRequest
import com.example.bukal.ai.QuizEvaluationViewModel
import com.example.bukal.ai.QuizExplanationRequest
import com.example.bukal.ai.QuizGenerationRequest
import com.example.bukal.ai.QuizGenerationViewModel
import com.example.bukal.ai.QuizResultSummary
import com.example.bukal.ai.QuizResultVerdict
import com.example.bukal.ai.toQuizQuestions
import com.example.bukal.ui.components.MainDestination
import com.example.bukal.ui.generating.GeneratingScreen
import com.example.bukal.ui.generating.GeneratingUiState
import com.example.bukal.ui.history.HistoryScreen
import com.example.bukal.ui.history.HistoryViewModel
import com.example.bukal.ui.home.HomeScreen
import com.example.bukal.ui.home.HomeMaterial
import com.example.bukal.ui.home.HomeUiState
import com.example.bukal.data.importing.SupportedDocumentType
import com.example.bukal.ui.modelsetup.ModelSetupScreen
import com.example.bukal.data.model.DefaultQuizModelId
import com.example.bukal.data.model.ModelDownloadSpec
import com.example.bukal.data.model.ModelInstallStatus
import com.example.bukal.data.model.ModelInstallationManager
import com.example.bukal.data.model.ModelInstallationSnapshot
import com.example.bukal.data.model.ModelPreferences
import com.example.bukal.data.model.ModelPurpose
import com.example.bukal.data.model.resolveSelectedQuizModelId
import com.example.bukal.ui.library.ImportStatus
import com.example.bukal.ui.library.EmbeddingIndexStatus
import com.example.bukal.ui.library.LibraryViewModel
import com.example.bukal.R
import com.example.bukal.ui.passageselection.PassageSelectionScreen
import com.example.bukal.ui.passageselection.PassagePreview
import com.example.bukal.ui.passageselection.PassageSelectionUiState
import com.example.bukal.ui.profile.ProfileScreen
import com.example.bukal.ui.profile.ProfileViewModel
import com.example.bukal.ui.quiz.QuizScreen
import com.example.bukal.ui.quiz.QuizUiState
import com.example.bukal.ui.quizsetup.QuizSetupScreen
import com.example.bukal.ui.quizsetup.QuizSetupUiState
import com.example.bukal.ui.quizsetup.QuizType
import com.example.bukal.ui.quizsetup.calculateQuestionDistribution
import com.example.bukal.ui.results.ResultsScreen
import com.example.bukal.ui.results.ResultItem
import com.example.bukal.ui.results.ResultStatus
import com.example.bukal.ui.results.ResultsUiState
import com.example.bukal.ui.settings.SettingsScreen
import com.example.bukal.ui.settings.SettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppDestination(val route: String) {
    MODEL_SETUP("model-setup"),
    HOME("home"),
    PASSAGE_SELECTION("passage-selection"),
    QUIZ_SETUP("quiz-setup"),
    GENERATING("generating"),
    QUIZ("quiz"),
    CHECKING("checking"),
    RESULTS("results"),
    HISTORY("history"),
    PROFILE("profile"),
    SETTINGS("settings"),
}

@Composable
fun BukalApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val libraryViewModel: LibraryViewModel = viewModel()
    val libraryState by libraryViewModel.uiState.collectAsState()
    val quizGenerationViewModel: QuizGenerationViewModel = viewModel()
    val quizGenerationState by quizGenerationViewModel.uiState.collectAsState()
    val quizEvaluationViewModel: QuizEvaluationViewModel = viewModel()
    val quizEvaluationState by quizEvaluationViewModel.uiState.collectAsState()
    val historyViewModel: HistoryViewModel = viewModel()
    val historyState by historyViewModel.uiState.collectAsState()
    val profileViewModel: ProfileViewModel = viewModel()
    val profileState by profileViewModel.uiState.collectAsState()
    val appContext = LocalContext.current.applicationContext
    val modelManager = remember(appContext) { ModelInstallationManager(appContext) }
    val modelPreferences = remember(appContext) { ModelPreferences(appContext) }
    val preferredQuizModelId by modelPreferences.selectedQuizModelId.collectAsState(
        initial = DefaultQuizModelId,
    )
    val coroutineScope = rememberCoroutineScope()
    var modelState by remember { mutableStateOf(ModelInstallationSnapshot.checking()) }
    var modelActionError by remember { mutableStateOf<String?>(null) }
    var activeMaterialId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedPassageId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeQuizModelId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeQuizMaterialName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedQuizTypes = remember {
        mutableStateListOf<QuizType>().apply { addAll(QuizType.entries) }
    }
    var currentQuestion by rememberSaveable { mutableIntStateOf(1) }
    val selectedOptions = remember { mutableStateMapOf<String, Int>() }
    val textResponses = remember { mutableStateMapOf<String, String>() }
    val matchingSelections = remember { mutableStateMapOf<String, String>() }
    var activeGenerationRequest by remember { mutableStateOf<QuizGenerationRequest?>(null) }
    var vectorSearchQuery by rememberSaveable { mutableStateOf("") }
    var resultsState by remember { mutableStateOf(ResultsUiState.mock) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) libraryViewModel.import(uri)
    }
    val recentMaterial = libraryState.recentMaterial
    val activeMaterial = activeMaterialId?.let { materialId ->
        libraryState.materials.firstOrNull { it.material.id == materialId }
    } ?: recentMaterial
    val selectedQuizModelId = resolveSelectedQuizModelId(modelState, preferredQuizModelId)
    val selectedQuizModel = modelState.models.firstOrNull { it.spec.id == selectedQuizModelId }?.spec

    val onMainDestinationSelected: (MainDestination) -> Unit = { destination ->
        navController.navigateToMain(destination)
    }

    val installRequiredModels: () -> Unit = {
        modelActionError = null
        coroutineScope.launch {
            modelActionError = withContext(Dispatchers.IO) {
                modelManager.installRequired()
            }
            modelState = withContext(Dispatchers.IO) { modelManager.inspect() }
        }
    }

    val installModel: (ModelDownloadSpec) -> Unit = { spec ->
        modelActionError = null
        coroutineScope.launch {
            modelActionError = withContext(Dispatchers.IO) { modelManager.install(spec) }
            modelState = withContext(Dispatchers.IO) { modelManager.inspect() }
        }
    }

    val deleteModel: (ModelDownloadSpec) -> Unit = { spec ->
        modelActionError = null
        coroutineScope.launch {
            modelActionError = withContext(Dispatchers.IO) { modelManager.delete(spec) }
            modelState = withContext(Dispatchers.IO) { modelManager.inspect() }
        }
    }

    val selectQuizModel: (ModelDownloadSpec) -> Unit = { spec ->
        if (spec.purpose == ModelPurpose.QUIZ &&
            modelState.models.any {
                it.spec.id == spec.id && it.status == ModelInstallStatus.Installed
            }
        ) {
            coroutineScope.launch { modelPreferences.selectQuizModel(spec.id) }
        }
    }

    LaunchedEffect(modelManager) {
        while (isActive) {
            modelState = withContext(Dispatchers.IO) { modelManager.inspect() }
            delay(if (modelState.hasActiveDownloads) 500L else 2_000L)
        }
    }

    LaunchedEffect(modelState.isReady) {
        if (modelState.isReady) libraryViewModel.indexPendingChunks()
    }

    LaunchedEffect(modelState.checked, selectedQuizModelId, preferredQuizModelId) {
        if (modelState.checked && selectedQuizModelId != preferredQuizModelId) {
            modelPreferences.selectQuizModel(selectedQuizModelId)
        }
    }

    LaunchedEffect(libraryState.newlyImportedMaterialId) {
        val importedId = libraryState.newlyImportedMaterialId ?: return@LaunchedEffect
        activeMaterialId = importedId
        selectedPassageId = null
        libraryViewModel.consumeNewImport()
        navController.navigate(AppDestination.PASSAGE_SELECTION.route) {
            launchSingleTop = true
        }
    }

    if (!modelState.isReady) {
        ModelSetupScreen(
            state = modelState,
            selectedQuizModelId = selectedQuizModelId,
            actionError = modelActionError,
            onInstallRequiredClick = installRequiredModels,
            onInstallModel = installModel,
            onSelectQuizModel = selectQuizModel,
            onDeleteModel = deleteModel,
            onContinueClick = {},
            onHelpClick = {},
            modifier = modifier,
        )
        return
    }

    NavHost(
        navController = navController,
        startDestination = AppDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(AppDestination.MODEL_SETUP.route) {
            ModelSetupScreen(
                state = modelState,
                selectedQuizModelId = selectedQuizModelId,
                actionError = modelActionError,
                onInstallRequiredClick = installRequiredModels,
                onInstallModel = installModel,
                onSelectQuizModel = selectQuizModel,
                onDeleteModel = deleteModel,
                onContinueClick = {
                    if (modelState.isReady) {
                        if (!navController.popBackStack()) {
                            navController.navigateHomeAndClearFlow()
                        }
                    }
                },
                onHelpClick = {},
            )
        }
        composable(AppDestination.HOME.route) {
            HomeScreen(
                state = HomeUiState(
                    materials = libraryState.materials.map { imported ->
                        HomeMaterial(
                            id = imported.material.id,
                            name = imported.material.displayName,
                            details = "${imported.passages.size} passages • " +
                                "${imported.material.documentFormat.uppercase()} • Stored locally",
                        )
                    },
                    streakLabel = if (profileState.currentStreakDays == 0) {
                        stringResource(R.string.home_start_streak)
                    } else {
                        pluralStringResource(
                            R.plurals.home_study_streak,
                            profileState.currentStreakDays,
                            profileState.currentStreakDays,
                        )
                    },
                    isImporting = libraryState.importStatus == ImportStatus.IMPORTING,
                    importError = libraryState.importError,
                    embeddingStatus = if (recentMaterial == null) {
                        null
                    } else {
                        when (libraryState.embeddingIndexStatus) {
                            EmbeddingIndexStatus.IDLE -> null
                            EmbeddingIndexStatus.INDEXING -> stringResource(R.string.home_embedding_indexing)
                            EmbeddingIndexStatus.READY -> stringResource(R.string.home_embedding_ready)
                            EmbeddingIndexStatus.ERROR -> libraryState.embeddingError
                        }
                    },
                    isEmbedding = libraryState.embeddingIndexStatus == EmbeddingIndexStatus.INDEXING,
                    embeddingError = libraryState.embeddingIndexStatus == EmbeddingIndexStatus.ERROR,
                ),
                onImportClick = {
                    filePicker.launch(SupportedDocumentType.pickerMimeTypes)
                },
                onMaterialClick = { materialId ->
                    libraryState.materials.firstOrNull { it.material.id == materialId }?.let {
                        activeMaterialId = materialId
                        selectedPassageId = null
                        navController.navigate(AppDestination.PASSAGE_SELECTION.route)
                    }
                },
                onRetryEmbeddingClick = libraryViewModel::indexPendingChunks,
                onMenuClick = {
                    navController.navigate(AppDestination.MODEL_SETUP.route)
                },
                onDestinationSelected = onMainDestinationSelected,
            )
        }
        composable(AppDestination.PASSAGE_SELECTION.route) {
            val selectionState = activeMaterial?.let { imported ->
                PassageSelectionUiState(
                    materialName = imported.material.displayName,
                    passages = imported.passages.map { passage ->
                        PassagePreview(
                            id = passage.sourceId,
                            title = passage.title ?: passage.sourceId,
                            excerpt = passage.content.replace('\n', ' '),
                        )
                    },
                    selectedPassageId = selectedPassageId,
                )
            } ?: PassageSelectionUiState(
                materialName = "No imported lesson",
                passages = emptyList(),
                selectedPassageId = null,
            )
            PassageSelectionScreen(
                state = selectionState,
                onBackClick = navController::popBackStack,
                onPassageSelected = { selectedPassageId = it },
                onContinueClick = {
                    if (selectedPassageId != null) {
                        navController.navigate(AppDestination.QUIZ_SETUP.route)
                    }
                },
                onDestinationSelected = onMainDestinationSelected,
            )
        }
        composable(AppDestination.QUIZ_SETUP.route) {
            val passage = activeMaterial?.passages?.firstOrNull { it.sourceId == selectedPassageId }
            QuizSetupScreen(
                state = QuizSetupUiState.mock.copy(
                    passageId = passage?.sourceId ?: QuizSetupUiState.mock.passageId,
                    passageTitle = passage?.title ?: QuizSetupUiState.mock.passageTitle,
                    selectedTypes = selectedQuizTypes.toList(),
                ),
                onBackClick = navController::popBackStack,
                onQuizTypeToggled = { quizType ->
                    if (quizType in selectedQuizTypes) {
                        selectedQuizTypes.remove(quizType)
                    } else {
                        selectedQuizTypes.add(quizType)
                    }
                },
                onGenerateClick = {
                    val activePassage = passage
                    val activeModel = selectedQuizModel
                    if (selectedQuizTypes.isNotEmpty() && activePassage != null && activeModel != null) {
                        activeQuizModelId = activeModel.id
                        activeQuizMaterialName = activeMaterial.material.displayName
                        currentQuestion = 1
                        selectedOptions.clear()
                        textResponses.clear()
                        matchingSelections.clear()
                        val request = QuizGenerationRequest(
                            model = activeModel,
                            passage = activePassage,
                            typeCounts = calculateQuestionDistribution(selectedQuizTypes)
                                .associateTo(linkedMapOf()) { it.type.questionType to it.count },
                        )
                        activeGenerationRequest = request
                        quizGenerationViewModel.clearQuiz()
                        quizEvaluationViewModel.clearResult()
                        quizGenerationViewModel.generate(request)
                        navController.navigate(AppDestination.GENERATING.route)
                    }
                },
            )
        }
        composable(AppDestination.GENERATING.route) {
            LaunchedEffect(quizGenerationState.questions, quizGenerationState.isGenerating) {
                if (quizGenerationState.isGenerating || quizGenerationState.questions.isEmpty()) {
                    return@LaunchedEffect
                }
                navController.navigate(AppDestination.QUIZ.route) {
                    popUpTo(AppDestination.GENERATING.route) { inclusive = true }
                }
            }
            GeneratingScreen(
                state = GeneratingUiState.mock.copy(
                    modelName = modelState.models
                        .firstOrNull { it.spec.id == activeQuizModelId }
                        ?.spec
                        ?.displayName
                        ?: selectedQuizModel?.displayName
                        ?: GeneratingUiState.mock.modelName,
                    passageId = selectedPassageId ?: GeneratingUiState.mock.passageId,
                    passageTitle = activeGenerationRequest?.passage?.title
                        ?: GeneratingUiState.mock.passageTitle,
                    currentQuestionNumber = quizGenerationState.currentQuestionNumber,
                    totalQuestions = quizGenerationState.totalQuestions,
                    errorMessage = quizGenerationState.errorMessage,
                ),
                onBackClick = {
                    quizGenerationViewModel.cancel()
                    navController.popBackStack()
                },
                onCancelClick = {
                    quizGenerationViewModel.cancel()
                    navController.popBackStack()
                },
                onRetryClick = {
                    activeGenerationRequest?.let(quizGenerationViewModel::generate)
                },
            )
        }
        composable(AppDestination.QUIZ.route) {
            val questions = quizGenerationState.questions
            val question = questions.getOrNull(currentQuestion - 1)
            val answer = question?.answer
            QuizScreen(
                state = if (question == null) {
                    QuizUiState.mock
                } else {
                    QuizUiState(
                        materialName = activeQuizMaterialName
                            ?: QuizUiState.mock.materialName,
                        passageId = question.sourceId,
                        currentQuestion = currentQuestion,
                        totalQuestions = questions.size,
                        questionType = question.type,
                        prompt = question.prompt,
                        options = (answer as? QuestionAnswer.MultipleChoice)?.options.orEmpty(),
                        selectedOptionIndex = selectedOptions[question.id],
                        textResponse = textResponses[question.id].orEmpty(),
                        matchingPairs = (answer as? QuestionAnswer.Matching)?.pairs.orEmpty(),
                        matchingSelections = (answer as? QuestionAnswer.Matching)
                            ?.pairs
                            .orEmpty()
                            .mapNotNull { pair ->
                                matchingSelections["${question.id}:${pair.leftId}"]
                                    ?.let { pair.leftId to it }
                            }
                            .toMap(),
                        failedQuestionCount = quizGenerationState.failedQuestionCount,
                    )
                },
                onCloseClick = navController::navigateHomeAndClearFlow,
                onMenuClick = {},
                onOptionSelected = { index ->
                    question?.let { selectedOptions[it.id] = index }
                },
                onTextResponseChanged = { response ->
                    question?.let { textResponses[it.id] = response }
                },
                onMatchingSelected = { leftId, rightId ->
                    question?.let { matchingSelections["${it.id}:$leftId"] = rightId }
                },
                onPreviousClick = {
                    currentQuestion = (currentQuestion - 1).coerceAtLeast(1)
                },
                onNextClick = {
                    if (currentQuestion < questions.size) {
                        currentQuestion += 1
                    } else {
                        activeGenerationRequest?.let { generationRequest ->
                            quizEvaluationViewModel.clearResult()
                            quizEvaluationViewModel.evaluate(
                                QuizEvaluationRequest(
                                    model = generationRequest.model,
                                    passage = generationRequest.passage,
                                    questions = questions,
                                    selectedOptions = selectedOptions.toMap(),
                                    textResponses = textResponses.toMap(),
                                    matchingSelections = matchingSelections.toMap(),
                                ),
                                savedQuizId = quizGenerationState.savedQuizId,
                            )
                            navController.navigate(AppDestination.CHECKING.route)
                        }
                    }
                },
            )
        }
        composable(AppDestination.CHECKING.route) {
            val questions = quizGenerationState.questions
            val writtenAnswerCount = questions.count { it.answer is QuestionAnswer.OpenResponse }
            LaunchedEffect(quizEvaluationState.result) {
                val result = quizEvaluationState.result ?: return@LaunchedEffect
                val passage = activeGenerationRequest?.passage ?: return@LaunchedEffect
                resultsState = result.toResultsUiState(
                    materialName = activeQuizMaterialName.orEmpty(),
                    passage = passage,
                )
                navController.navigate(AppDestination.RESULTS.route) {
                    popUpTo(AppDestination.QUIZ.route) { inclusive = true }
                }
            }
            CheckingScreen(
                state = CheckingUiState(
                    deterministicAnswerCount = questions.size - writtenAnswerCount,
                    writtenAnswerCount = writtenAnswerCount,
                    errorMessage = quizEvaluationState.errorMessage,
                ),
                onRetryClick = {
                    val generationRequest = activeGenerationRequest
                    if (generationRequest != null) {
                        quizEvaluationViewModel.evaluate(
                            QuizEvaluationRequest(
                                model = generationRequest.model,
                                passage = generationRequest.passage,
                                questions = questions,
                                selectedOptions = selectedOptions.toMap(),
                                textResponses = textResponses.toMap(),
                                matchingSelections = matchingSelections.toMap(),
                            ),
                            savedQuizId = quizGenerationState.savedQuizId,
                        )
                    }
                },
            )
        }
        composable(AppDestination.RESULTS.route) {
            ResultsScreen(
                state = resultsState,
                onCloseClick = navController::navigateHomeAndClearFlow,
                onResultToggle = { itemId ->
                    resultsState = resultsState.copy(
                        items = resultsState.items.map { item ->
                            if (item.id == itemId) item.copy(expanded = !item.expanded) else item
                        },
                    )
                },
                explanations = quizEvaluationState.explanations,
                explainingQuestionId = quizEvaluationState.explainingQuestionId,
                explanationErrors = quizEvaluationState.explanationErrors,
                onExplainClick = { itemId ->
                    val generationRequest = activeGenerationRequest
                    val result = quizEvaluationState.result?.items?.firstOrNull {
                        it.question.id == itemId
                    }
                    if (generationRequest != null && result != null) {
                        quizEvaluationViewModel.explain(
                            QuizExplanationRequest(
                                model = generationRequest.model,
                                passage = generationRequest.passage,
                                result = result,
                            ),
                        )
                    }
                },
                onDoneClick = {
                    profileViewModel.refresh()
                    navController.navigateHomeAndClearFlow()
                },
            )
        }
        composable(AppDestination.HISTORY.route) {
            LaunchedEffect(Unit) {
                historyViewModel.refresh()
            }
            HistoryScreen(
                state = historyState,
                onMenuClick = {
                    navController.navigate(AppDestination.MODEL_SETUP.route)
                },
                onAttemptClick = { attemptId ->
                    coroutineScope.launch {
                        runCatching { historyViewModel.getSavedQuiz(attemptId) }
                            .onSuccess { savedQuiz ->
                                val retakeModel = modelState.models.firstOrNull {
                                    it.spec.id == savedQuiz.attempt.quizModelId &&
                                        it.status == ModelInstallStatus.Installed
                                }?.spec ?: selectedQuizModel
                                if (retakeModel == null) {
                                    historyViewModel.showError(
                                        "Install a quiz model before retaking this quiz.",
                                    )
                                    return@onSuccess
                                }
                                val questions = savedQuiz.toQuizQuestions()
                                activeMaterialId = savedQuiz.passage.materialId
                                selectedPassageId = savedQuiz.passage.sourceId
                                activeQuizModelId = retakeModel.id
                                activeQuizMaterialName = savedQuiz.materialName
                                currentQuestion = 1
                                selectedOptions.clear()
                                textResponses.clear()
                                matchingSelections.clear()
                                activeGenerationRequest = QuizGenerationRequest(
                                    model = retakeModel,
                                    passage = savedQuiz.passage,
                                    typeCounts = questions.groupingBy { it.type }.eachCount(),
                                )
                                quizGenerationViewModel.useSavedQuiz(
                                    questions = questions,
                                    savedDraftId = savedQuiz.attempt.id,
                                )
                                quizEvaluationViewModel.clearResult()
                                navController.navigate(AppDestination.QUIZ.route)
                            }
                            .onFailure { error ->
                                historyViewModel.showError(
                                    error.message ?: "This saved quiz could not be opened.",
                                )
                            }
                    }
                },
                onDestinationSelected = onMainDestinationSelected,
            )
        }
        composable(AppDestination.PROFILE.route) {
            LaunchedEffect(Unit) {
                profileViewModel.refresh()
            }
            ProfileScreen(
                state = profileState,
                onMenuClick = {
                    navController.navigate(AppDestination.SETTINGS.route)
                },
                onYearSelected = profileViewModel::selectYear,
                onDestinationSelected = onMainDestinationSelected,
            )
        }
        composable(AppDestination.SETTINGS.route) {
            SettingsScreen(
                state = SettingsUiState(
                    query = vectorSearchQuery,
                    searchStatus = libraryState.vectorSearchStatus,
                    results = libraryState.vectorSearchResults,
                    errorMessage = libraryState.vectorSearchError,
                ),
                onQueryChange = { vectorSearchQuery = it },
                onSearchClick = { libraryViewModel.searchByMeaning(vectorSearchQuery) },
                onManageModelsClick = {
                    navController.navigate(AppDestination.MODEL_SETUP.route)
                },
                onBackClick = navController::popBackStack,
            )
        }
    }
}

private fun QuizResultSummary.toResultsUiState(
    materialName: String,
    passage: com.example.bukal.data.local.PassageEntity,
): ResultsUiState {
    val expandedId = items.firstOrNull { it.verdict != QuizResultVerdict.CORRECT }?.question?.id
    val resultItems = items.map { item ->
        ResultItem(
            id = item.question.id,
            questionType = item.question.type,
            question = item.question.prompt,
            status = when (item.verdict) {
                QuizResultVerdict.CORRECT -> ResultStatus.CORRECT
                QuizResultVerdict.INCORRECT -> ResultStatus.INCORRECT
                QuizResultVerdict.UNANSWERED -> ResultStatus.UNANSWERED
            },
            isAiEvaluated = item.wasAiEvaluated,
            learnerAnswer = item.learnerAnswer,
            expectedAnswer = item.expectedAnswer,
            sourceId = item.question.sourceId,
            expanded = item.question.id == expandedId,
        )
    }
    val message = when {
        possiblePoints == 0.0 -> "Review complete"
        earnedPoints / possiblePoints >= 0.8 -> "Great work"
        earnedPoints / possiblePoints >= 0.5 -> "Good effort"
        else -> "Keep learning"
    }
    return ResultsUiState(
        earnedPoints = earnedPoints,
        possiblePoints = possiblePoints,
        message = message,
        materialName = materialName,
        questionCount = items.size,
        passageTitle = passage.title ?: passage.sourceId,
        passageSourceId = passage.sourceId,
        passageContent = passage.content,
        items = resultItems,
    )
}

private fun NavHostController.navigateToMain(destination: MainDestination) {
    val route = when (destination) {
        MainDestination.HOME -> AppDestination.HOME.route
        MainDestination.HISTORY -> AppDestination.HISTORY.route
        MainDestination.PROFILE -> AppDestination.PROFILE.route
    }
    navigate(route) {
        popUpTo(AppDestination.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateHomeAndClearFlow() {
    navigate(AppDestination.HOME.route) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}
