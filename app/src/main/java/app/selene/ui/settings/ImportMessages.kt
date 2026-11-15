package app.selene.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.selene.R
import app.selene.data.json.ImportError
import app.selene.ui.DateFormats

@Composable
fun importFailureMessage(failure: ImportFailure): String = when (failure) {
    ImportFailure.Unreadable -> stringResource(R.string.import_error_unreadable)
    ImportFailure.TooLarge -> stringResource(R.string.import_error_too_large)
    is ImportFailure.Invalid -> importErrorMessage(failure.error)
}

@Composable
fun importErrorMessage(error: ImportError): String = when (error) {
    ImportError.NotJson -> stringResource(R.string.import_error_not_json)
    ImportError.NotSeleneExport -> stringResource(R.string.import_error_format)
    ImportError.MissingVersion -> stringResource(R.string.import_error_version_missing)
    is ImportError.UnsupportedVersion -> stringResource(R.string.import_error_version_unsupported, error.found, error.supported)
    is ImportError.Structure -> stringResource(R.string.import_error_structure, error.detail)
    is ImportError.InvalidDate -> stringResource(R.string.import_error_date, error.index, error.value)
    is ImportError.DuplicateDate -> stringResource(R.string.import_error_duplicate_date, DateFormats.dayMonthYear(error.date))
    is ImportError.OutOfRange -> stringResource(
        R.string.import_error_range,
        DateFormats.dayMonthYear(error.date).replaceFirstChar { it.uppercase() },
        stringResource(
            when (error.field) {
                ImportError.Field.FLOW -> R.string.import_field_flow
                ImportError.Field.PAIN -> R.string.import_field_pain
                ImportError.Field.FATIGUE -> R.string.import_field_fatigue
                ImportError.Field.MOOD -> R.string.import_field_mood
            },
        ),
        error.value,
        error.range.first,
        error.range.last,
    )
    is ImportError.PeriodWithoutMood -> stringResource(R.string.import_error_period_without_mood, DateFormats.dayMonthYear(error.date).replaceFirstChar { it.uppercase() })
    is ImportError.InvalidEventTitle -> stringResource(R.string.import_error_event_title, DateFormats.dayMonthYear(error.date).replaceFirstChar { it.uppercase() })
    is ImportError.InvalidEventId -> stringResource(R.string.import_error_event_id, DateFormats.dayMonthYear(error.date).replaceFirstChar { it.uppercase() }, error.id)
}
