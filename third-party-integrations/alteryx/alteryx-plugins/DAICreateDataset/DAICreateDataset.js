const displayBrowserOption = () => {
    if (Alteryx.Gui.Manager.getDataItem('DoAutoViz').getValue()) {
        document.getElementById('displaybrowser').style.display = 'initial'
    } else {
        document.getElementById('displaybrowser').style.display = 'none'
    }
}

const toggleOAuth = () => {
    if (Alteryx.Gui.Manager.getDataItem('UseOAuth').getValue()) {
        document.getElementById('usernameBox').style.display = 'none'
        document.getElementById('passwordBox').style.display = 'none'

        document.getElementById('endpointURL').style.display = 'initial'
        document.getElementById('introspectionURL').style.display = 'initial'
        document.getElementById('clientID').style.display = 'initial'
        document.getElementById('refreshToken').style.display = 'initial'
    } else {
        document.getElementById('usernameBox').style.display = 'initial'
        document.getElementById('passwordBox').style.display = 'initial'

        document.getElementById('endpointURL').style.display = 'none'
        document.getElementById('introspectionURL').style.display = 'none'
        document.getElementById('clientID').style.display = 'none'
        document.getElementById('refreshToken').style.display = 'none'
    }
}

Alteryx.Gui.BeforeLoad = function (manager, AlteryxDataItems, json) {
  const operationSelectDatasetSource = new AlteryxDataItems.StringSelector('DataImportSource', {
    optionList: [
      {label: 'Filesystem', value: 'filesystem'},
      {label: 'Alteryx', value: 'upload'},
      {label: 'Azure Blob Store', value: 'azure'},
      {label: 'Amazon S3', value: 's3'},
      {label: 'Google Cloud Storage', value: 'gcs'}
    ]
  })
  operationSelectDatasetSource.setValue('upload')
  manager.addDataItem(operationSelectDatasetSource)
  manager.bindDataItemToWidget(operationSelectDatasetSource, 'DataSourceDropdown')
  displayBrowserOption()
  toggleOAuth()
}

Alteryx.Gui.AfterLoad = function (manager) {
    displayBrowserOption()
    toggleOAuth()
    Alteryx.Gui.Manager.getDataItem('DoAutoViz').registerPropertyListener('value', displayBrowserOption)
    Alteryx.Gui.Manager.getDataItem('UseOAuth').registerPropertyListener('value', toggleOAuth)
}