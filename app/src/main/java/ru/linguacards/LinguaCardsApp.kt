package ru.linguacards

import android.app.Application
import ru.linguacards.data.StudyRepository

class LinguaCardsApp : Application() {
    val repository by lazy { StudyRepository.get(this) }
}
