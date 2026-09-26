import { memo } from 'react'
import { canUseRule, impliedRules } from '../../domain/configRules'
import { USER_RULES, type FieldKey, type TargetField, type UserRule } from '../../domain/types'
import { messages } from '../../shared/messages'
import styles from './RulesStep.module.css'

interface ValidationEditorProps {
  field: TargetField
  enabled: readonly UserRule[]
  onToggle: (key: FieldKey, rule: UserRule, enabled: boolean) => void
}

/**
 * Rule kiểm tra của một field (spec rule-config): rule suy ra từ schema hiện chỉ đọc; `email` (chỉ field `string`) và
 * `unique` do user bật/tắt.
 */
export const ValidationEditor = memo(function ValidationEditor({ field, enabled, onToggle }: ValidationEditorProps) {
  return (
    <fieldset className={styles.section}>
      <legend className={styles.sectionTitle}>{messages.rules.validationsLegend}</legend>
      <ul aria-label={messages.rules.impliedLabel} className={styles.chips}>
        {impliedRules(field).map((rule) => (
          <li key={rule}>{rule}</li>
        ))}
      </ul>
      <div className={styles.toggles}>
        {USER_RULES.filter((rule) => canUseRule(field, rule)).map((rule) => (
          <label key={rule} className={styles.toggle}>
            <input
              type="checkbox"
              checked={enabled.includes(rule)}
              onChange={(event) => onToggle(field.key, rule, event.target.checked)}
            />
            {messages.rules.ruleOptions[rule]}
          </label>
        ))}
      </div>
    </fieldset>
  )
})
