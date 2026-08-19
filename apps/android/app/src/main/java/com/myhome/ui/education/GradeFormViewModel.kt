package com.myhome.ui.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.net.dto.GradeCreateRequest
import com.myhome.net.dto.GradeDto
import com.myhome.net.dto.GradeUpdateRequest
import com.myhome.net.dto.MemberInfo
import com.myhome.repo.CourseRepository
import com.myhome.repo.FamilyRepository
import com.myhome.repo.GradeRepository
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GradeFormUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val subjects: List<String> = emptyList(), // 系统学科下拉（13 种）
    val children: List<MemberInfo> = emptyList(),
    val existing: GradeDto? = null,
    val error: String? = null,
)

@HiltViewModel
class GradeFormViewModel @Inject constructor(
    private val gradeRepo: GradeRepository,
    private val courseRepo: CourseRepository,
    private val familyRepo: FamilyRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(GradeFormUiState())
    val ui: StateFlow<GradeFormUiState> = _ui.asStateFlow()

    fun init(gradeId: String?) {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val coursesDeferred = async { runCatching { courseRepo.list() } }
            val membersDeferred = async { runCatching { familyRepo.listMembers() } }
            val courses = coursesDeferred.await().getOrNull().orEmpty()
            val members = membersDeferred.await().getOrNull().orEmpty()
                .filter { it.isActive && "child" in it.roles }
            val subjects = courses.map { it.subject }.distinct()
            // 编辑模式：从列表取（listGrades 已含 family_id 过滤后的全家成绩）
            val existing = if (gradeId != null) {
                runCatching { gradeRepo.list().firstOrNull { it.id == gradeId } }.getOrNull()
            } else null
            _ui.update {
                it.copy(loading = false, subjects = subjects, children = members, existing = existing)
            }
        }
    }

    fun save(
        id: String?,
        subject: String,
        score: Double,
        scoreFull: Double,
        examName: String?,
        examDate: String,
        note: String?,
        assigneeUserId: String,
        onDone: () -> Unit,
    ) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (id == null) {
                    gradeRepo.create(
                        GradeCreateRequest(
                            subject = subject,
                            score = score,
                            scoreFull = scoreFull,
                            examName = examName,
                            examDate = examDate,
                            note = note,
                            assigneeUserId = assigneeUserId,
                        )
                    )
                } else {
                    gradeRepo.update(
                        id,
                        GradeUpdateRequest(
                            subject = subject,
                            score = score,
                            scoreFull = scoreFull,
                            examName = examName,
                            examDate = examDate,
                            note = note,
                            assigneeUserId = assigneeUserId,
                        ),
                    )
                }
            }
                .onSuccess { _ui.update { it.copy(saving = false) }; onDone() }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }

    fun delete(gradeId: String, onDone: () -> Unit) {
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { gradeRepo.delete(gradeId) }
                .onSuccess { _ui.update { it.copy(saving = false) }; onDone() }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = friendlyError(e)) } }
        }
    }
}
