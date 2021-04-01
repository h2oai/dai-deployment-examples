const displayBrowserOption = () => {
    if (Alteryx.Gui.Manager.getDataItem('DoAutoViz').getValue()) {
        document.getElementById('displaybrowser').style.display = 'initial'
    } else {
        document.getElementById('displaybrowser').style.display = 'none'
    }
}

const toggleKeycloak = () => {
    if (Alteryx.Gui.Manager.getDataItem('UseKeycloak').getValue()) {
        document.getElementById('keycloakServerURL').style.display = 'initial'
        document.getElementById('keycloakTokenEndpoint').style.display = 'initial'
        document.getElementById('keycloakRealm').style.display = 'initial'
        document.getElementById('clientID').style.display = 'initial'
    } else {
        document.getElementById('keycloakServerURL').style.display = 'none'
        document.getElementById('keycloakTokenEndpoint').style.display = 'none'
        document.getElementById('keycloakRealm').style.display = 'none'
        document.getElementById('clientID').style.display = 'none'
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
  toggleKeycloak()
}

Alteryx.Gui.AfterLoad = function (manager) {
    displayBrowserOption()
    toggleKeycloak()
    Alteryx.Gui.Manager.getDataItem('DoAutoViz').registerPropertyListener('value', displayBrowserOption)
    Alteryx.Gui.Manager.getDataItem('UseKeycloak').registerPropertyListener('value', toggleKeycloak)
}