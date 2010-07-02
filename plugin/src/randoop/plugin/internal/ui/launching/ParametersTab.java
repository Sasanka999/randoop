package randoop.plugin.internal.ui.launching;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.debug.internal.ui.SWTFactory;

import randoop.plugin.internal.IConstants;
import randoop.plugin.internal.core.TestKinds;
import randoop.plugin.internal.ui.options.IOption;
import randoop.plugin.internal.ui.options.IOptionFactory;

public class ParametersTab extends OptionTab {
  private IOption fRandomSeed;
  private IOption fMaxTestSize;
  private IOption fUseThreads;
  private IOption fThreadTimeout;
  private IOption fUseNull;
  private IOption fNullRatio;

  private IOption fJUnitTestInputs;
  private IOption fTimeLimit;
  private IOption lConvertedTimeLimit;
  
  private IOption fTestKinds;
  private IOption fMaxTestsWritten;
  private IOption fMaxTestsPerFile;
  
  private ModifyListener fBasicModifyListener = new RandoopTabListener();
  private SelectionListener fBasicSelectionListener = new RandoopTabListener();
  
  private class RandoopTabListener extends SelectionAdapter implements
      ModifyListener {
    @Override
    public void widgetSelected(SelectionEvent e) {
      setErrorMessage(null);
      updateLaunchConfigurationDialog();
    }

    @Override
    public void modifyText(ModifyEvent e) {
      setErrorMessage(null);
      updateLaunchConfigurationDialog();
    }
  }
  
  /*
   * (non-Javadoc)
   * @see org.eclipse.debug.ui.ILaunchConfigurationTab#createControl(org.eclipse.swt.widgets.Composite)
   */
  public void createControl(Composite parent) {
    Composite comp = SWTFactory.createComposite(parent, 1, 1,
        GridData.FILL_HORIZONTAL);
    setControl(comp);

    createGeneralGroup(comp);
    createGenerationLimitGroup(comp);
    createOutputRestrictionsGroup(comp);

    Button bRestoreDefaults = new Button(comp, 0);
    bRestoreDefaults.setText("Restore Defaults");
    bRestoreDefaults.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
    bRestoreDefaults.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {

      }
    });
    bRestoreDefaults.setEnabled(true);
  }

  private void createGeneralGroup(Composite parent) {
    Group group = SWTFactory.createGroup(parent, "General", 1, 1,
        GridData.FILL_HORIZONTAL);

    Composite comp = SWTFactory
        .createComposite(group, 2, 1, GridData.FILL_BOTH);
    GridLayout ld = (GridLayout) comp.getLayout();
    ld.marginWidth = 1;
    ld.marginHeight = 1;

    SWTFactory.createLabel(comp, "Random &Seed:", 1);
    Text randomSeed = SWTFactory.createSingleText(comp, 1);
    fRandomSeed = IOptionFactory.createRandomSeedOption(randomSeed);

    SWTFactory.createLabel(comp, "Maximum Test Si&ze:", 1);
    Text maxTestSize = SWTFactory.createSingleText(comp, 1);
    fMaxTestSize = IOptionFactory.createMaximumTestSizeOption(maxTestSize);

    Button useThreads = createCheckButton(comp, "Thread Time&out:");
    useThreads.setSelection(true);
    
    Text threadTimeout = SWTFactory.createSingleText(comp, 1);
    fThreadTimeout = IOptionFactory.createThreadTimeoutOption(threadTimeout);
    fUseThreads = IOptionFactory.createUseThreads(fThreadTimeout, useThreads);
    
    Button useNull = createCheckButton(comp, "Null R&atio:");
    useNull.setSelection(false);
    
    Text nullRatio = SWTFactory.createSingleText(comp, 1);
    nullRatio.setEnabled(useNull.getSelection());
    fNullRatio = IOptionFactory.createNullRatioOption(nullRatio);
    fUseNull = IOptionFactory.createUseNull(fNullRatio, useNull);
    
    addOption(fRandomSeed);
    addOption(fMaxTestSize);
    addOption(fUseThreads);
    addOption(fUseNull);
    
    randomSeed.addModifyListener(fBasicModifyListener);
    maxTestSize.addModifyListener(fBasicModifyListener);
    useThreads.addSelectionListener(fBasicSelectionListener);
    threadTimeout.addModifyListener(fBasicModifyListener);
    useNull.addSelectionListener(fBasicSelectionListener);
    nullRatio.addModifyListener(fBasicModifyListener);
  }

  private void createGenerationLimitGroup(Composite parent) {
    Group group = SWTFactory.createGroup(parent, "Generation Limit", 1, 1,
        GridData.FILL_HORIZONTAL);

    Composite comp = SWTFactory
        .createComposite(group, 2, 2, GridData.FILL_BOTH);
    GridLayout ld = (GridLayout) comp.getLayout();
    ld.marginWidth = 1;
    ld.marginHeight = 1;

    SWTFactory.createLabel(comp, "JUnit Test &Inputs:", 1);
    Text junitTestInputs = SWTFactory.createSingleText(comp, 1);
    fJUnitTestInputs = IOptionFactory.createJUnitTestInputsOption(junitTestInputs);

    SWTFactory.createLabel(comp, "&Time Limit:", 1);
    Text timeLimit = SWTFactory.createSingleText(comp, 1);

    SWTFactory.createLabel(comp, IConstants.EMPTY_STRING, 1); // spacer
    Label convertedTimeLimit = SWTFactory.createLabel(comp, IConstants.EMPTY_STRING, 1);
    convertedTimeLimit.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true,
       false));
    
    fTimeLimit = IOptionFactory.createTimeLimitOption(timeLimit, convertedTimeLimit);

    addOption(fJUnitTestInputs);
    addOption(fTimeLimit);
    
    junitTestInputs.addModifyListener(fBasicModifyListener);
    timeLimit.addModifyListener(fBasicModifyListener);
  }
  
  private void createOutputRestrictionsGroup(Composite parent) {
    Group group = SWTFactory.createGroup(parent, "&Output Restrictions", 3, 1,
        GridData.FILL_HORIZONTAL);

    SWTFactory.createLabel(group, "Test &Kinds:", 1);
    Combo testKinds = SWTFactory.createCombo(group, SWT.READ_ONLY, 2, TestKinds
        .getTranslatableNames());
    fTestKinds = IOptionFactory.createTestKindsOption(testKinds);

    SWTFactory.createLabel(group, "Maximum Tests &Written:", 1);
    Text maxTestsWritten = SWTFactory.createSingleText(group, 2);
    fMaxTestsWritten = IOptionFactory.createMaximumTestsWrittenOption(maxTestsWritten);

    SWTFactory.createLabel(group, "Maximum Tests Per &File:", 1);
    Text maxTestsPerFile = SWTFactory.createSingleText(group, 2);
    fMaxTestsPerFile = IOptionFactory.createMaximumTestsPerFileOption(maxTestsPerFile);

    testKinds.addModifyListener(fBasicModifyListener);
    maxTestsWritten.addModifyListener(fBasicModifyListener);
    maxTestsPerFile.addModifyListener(fBasicModifyListener);

    addOption(fTestKinds);
    addOption(fMaxTestsWritten);
    addOption(fMaxTestsPerFile);
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.debug.ui.ILaunchConfigurationTab#getName()
   */
  @Override
  public String getName() {
    return "Parameters";
  }

  /*
   * (non-Javadoc)
   * @see org.eclipse.debug.ui.AbstractLaunchConfigurationTab#getId()
   */
  @Override
  public String getId() {
    return "randoop.plugin.ui.launching.parametersTab"; //$NON-NLS-1$
  }
  
}