import { DatasetSource } from '../../shared/dataset/DatasetSource'
import buttons from '../../shared/ui/Button.module.css'
import { ArrowRightIcon } from '../../shared/ui/icons'
import { useValidator } from './context'
import { validatorMessages as text } from './messages'
import styles from './Validator.module.css'

export function SourceStep() {
  const { state, dispatch } = useValidator()

  return (
    <section className={styles.step} aria-labelledby="validator-source-title">
      <h2 id="validator-source-title" className={styles.title} tabIndex={-1}>
        {text.source.title}
      </h2>
      <p className={styles.intro}>{text.source.intro}</p>
      <DatasetSource
        dataset={state.dataset}
        options={state.options}
        preview={state.preview}
        disabled={state.running}
        onDatasetChange={(dataset) => dispatch({ type: 'datasetChanged', dataset })}
        onOptionsChange={(options) => dispatch({ type: 'optionsChanged', options })}
        onPreviewLoaded={(preview) => dispatch({ type: 'previewLoaded', preview })}
      />
      <div className={styles.actions}>
        <button
          type="button"
          className={buttons.primary}
          disabled={!state.preview}
          onClick={() => dispatch({ type: 'navigate', step: 'schema' })}
        >
          {text.next}
          <ArrowRightIcon />
        </button>
      </div>
    </section>
  )
}
